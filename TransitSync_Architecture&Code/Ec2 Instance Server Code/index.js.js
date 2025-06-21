import express from "express";
import bodyParser from "body-parser";
import redis from "redis";
import http from "http";
import { Server } from "socket.io";
import { log } from "console";

const app = express();
const server = http.createServer(app);
const io = new Server(server);
const port = 8080;

// Redis configuration
const redisHost = "Redis_Host_Domain";
const redisPort = "PortNO";
const redisPassword = "Redis_Password";

const redisClient = redis.createClient({
  url: `redis://:${redisPassword}@${redisHost}:${redisPort}`,
});

await redisClient.connect().catch((err) => {
  console.error("Error connecting to Redis:", err);
  process.exit(1);
});


app.use(bodyParser.json());

// WebSocket connection
io.on("connection", (socket) => {
  console.log("A user connected:", socket.id);

  socket.on("location_update", async (data) => {
    if (!data?.driverId || !data?.latitude || !data?.longitude) {
      console.error("Invalid location update data received.");
      return;
    }

    try {
      const locationData = {
        latitude: data.latitude,
        longitude: data.longitude,
      };

      const dataStr = JSON.stringify(locationData);
      await redisClient.lPush(data.driverId, dataStr);
      await redisClient.lTrim(data.driverId, 0, 4);
      await redisClient.set(data.driverId+":isLive", 'true', { EX: 5*60 });

      console.log("Data added to the queue:", data);
    } catch (err) {
      console.error("Error handling location_update:", err);
    }

    io.emit("location_update_broadcast", data);
  });

  socket.on("disconnect", () => {
    console.log("User disconnected:", socket.id);
  });
});

app.get("/", (req, res) => {
  res.send("Health check");
});
//Adds Driver To Queue, both for stationary as well as inTransit
app.get("/addToQueue", async (req, res) => {
  try {
    console.log("addQueue");
    const { routeId, hubId, driverId, remove } = req.query;
    if (!routeId || !hubId || !driverId) {
      return res.status(400).send({ error: "Missing routeId, hubId, or driverId" });
    }
    console.log("remove:", remove);
    const key = `${routeId}:${hubId}`;

    // Get the highest score with zRange (REV = true)
    const result = await redisClient.zRange(key, 0, 0, { REV: true, WITHSCORES: true });
    let scoreMax = 1;

    if (result.length > 1) {
      const currentMaxScore = parseFloat(result[1]);
      scoreMax = currentMaxScore + 1;
    }

    if (remove === "true") {
      await redisClient.zAdd(`${key}:inTransit`, [{ score: scoreMax, value: driverId }]);
      await redisClient.zRem(key, driverId);
      console.log("driver added to intransit queue");
    } else {
      await redisClient.zAdd(key, [{ score: scoreMax, value: driverId }]);
      console.log("driver added to stationary queue");
    }

    res.status(200).send({ success: true });
  } catch (err) {
    console.error("Error in /addToQueue:", err);
    res.status(500).send({ error: "Internal Server Error" });
  }
});

// Remove driver from both queues
app.get("/removeFromQueue", async (req, res) => {
  try {
    const { routeId, hubId, driverId } = req.query;
    if (!routeId || !hubId || !driverId) {
      return res.status(400).send({ error: "Missing routeId, hubId, or driverId" });
    }
    const key = `${routeId}:${hubId}`;

    await redisClient.zRem(`${key}:inTransit`, driverId);
    await redisClient.zRem(key, driverId);
    console.log("driver removed from queue");

    res.status(200).send({ success: true });
  } catch (err) {
    console.error("Error in /removeFromQueue:", err);
    res.status(500).send({ error: "Internal Server Error" });
  }
});
const ID_TIMEOUT = 4000; // 4 seconds
const CLEANUP_INTERVAL = 8000; // 8 seconds

// Get driver ID from queue
app.get("/getDriverId", async (req, res) => {
  try {
    const { routeId, startHubId } = req.query;
    console.log("reached here baby");
    console.log(`routeId:${routeId} and startHubId:${startHubId}`);
   
    if (!routeId || !startHubId) {
      return res.status(400).json({ error: "Missing routeId or startHubId" });
    }
   
    const key = `${routeId}:${startHubId}`;
    const result = await redisClient.zRange(key, 0, 0);
    console.log("result sent:", result[0]);
   
    if (result.length === 0) {
      return res.status(404).json({ error: "No driver found" });
    }
   
    // Return JSON object with driverId property instead of raw string
    return res.status(200).json({ driverId: result[0] });
  } catch (err) {
    console.error("Error in /getDriverId:", err);
    res.status(500).json({ error: "Internal Server Error" });
  }
});

// Get all driver IDs in transit (Triggered First by TransitSyncPassenger after selecting the Route) 
app.get("/getDriverIdsInTransit", async (req, res) => {
  try {
    const { routeId, startHubId } = req.query;
   
    if (!routeId || !startHubId) {
      return res.status(400).json({ error: "Missing routeId, hubId" });
    }
   
    const key = `${routeId}:${startHubId}:inTransit`;
    const results = await redisClient.zRange(key, 0, -1);
    console.log("result sent", results);
    
    // Convert array of driver IDs to array of driver objects
    const driversResponse = results.map(driverId => ({ driverId }));
    
    // Return JSON array of driver objects
    return res.status(200).json(driversResponse);
  } catch (err) {
    console.error("Error in /getDriverIds:", err);
    res.status(500).json({ error: "Internal Server Error" });
  }
});
// Add passenger to a route (Helps Keep Passenger Count)
app.post("/addPassenger", async (req, res) => {
  console.log("InPassenger Request");
  const { id, routeId_startHub_passenger } = req.body;//routeId_startHub_passenger=routeID:StartHubId:passenger
  
  if (!id || !routeId_startHub_passenger) {
    return res.status(400).send({ error: "ID and routeId are required" });
  }

  try {
    const expirationTime = Date.now() + ID_TIMEOUT;
    
    await redisClient.zAdd(routeId_startHub_passenger, [
      {
        score: expirationTime,
        value: id,
      },
    ]);
    await redisClient.expire(routeId_startHub_passenger, 300);
    console.log(`ID ${id} added or refreshed.`);
    res.send({ success: true });
  } catch (err) {
    console.error("Error in /addPassenger:", err);
    res.status(500).send({ error: "Internal Server Error" });
  }
});


// Get active passenger count
app.get("/getPassengerCount", async (req, res) => {
  try {
    const { routeId, startHubId } = req.query;
    
    if (!routeId || !startHubId) {
      return res.status(400).send({ error: "Missing routeId, hubId" });
    }
    
    const key = `${routeId}:${startHubId}:passenger`;
    const count = await redisClient.zCard(key);
  
    
    console.log(`key:${key} and count:${count}`);
    
   return res.status(200).json({ count: count });
  } catch (err) {
    console.error("Error in /getPassengerCount:", err);
    res.status(500).send({ error: "Internal Server Error" });
  }
});
//driver queue Position
app.get("/driverQueuePos", async (req, res) => {
  try {
    console.log("driverQueuePos");
    
    const { routeId, startHubId, driverId } = req.query;
    console.log(`In driverPos routeID:"${routeId},hubId:${startHubId},driverId:${driverId}`);
    
    if (!routeId || !startHubId || !driverId) {
      return res.status(400).send({ error: "Missing routeId, hubId, or driverId" });
    }

    const key = `${routeId}:${startHubId}`;

    const driverScore = await redisClient.zScore(key, driverId);
    if (driverScore === null) {
      return res.status(404).send({ error: "Driver not found in queue" });
    }

    const driverPos = await redisClient.zCount(key, 0, driverScore - 1);
    console.log("driverPos result:",driverPos);
    
    console.log(`key: ${key}, driverId: ${driverId}, position: ${driverPos}`);

    return res.status(200).json({
      driverPos: driverPos + 1
    });
  } catch (err) {
    console.error("Error in /driverQueuePos:", err);
    res.status(500).send({ error: "Internal Server Error" });
  }
});
// Get latest driver location
app.get("/driverLocation", async (req, res) => {
  try {
    const { driverId } = req.query;
    
    if (!driverId) {
      return res.status(400).send({ error: "driverId is required" });
    }
  
    /*driverId:isLive key keeps track if the driver is active or not if not then delete the list of latLng 
    this indirectly will also delete the driverId from routeId:hubId queue as driverId key will become null 
    prompting the process to pop the driverId(Passenger side application-calls->/removeFromQueue) 
    */
    const val = await redisClient.get(driverId+":isLive");
    let latestItem = null;

    if (val === 'true') {
      latestItem = await redisClient.lIndex(driverId, 0);
    } else {
      // If driver isn't active, remove the list
      await redisClient.del(driverId);
    }

    if (!latestItem) {
      return res.status(404).send({ error: "No location found for driver" });
    }

    res.status(200).send(JSON.parse(latestItem));
  } catch (err) {
    console.error("Error in /driverLocation:", err);
    res.status(500).send({ error: "Internal Server Error" });
  }
});
/*
The Two methods /getPassengerIdsForExpiryCheck and /postDelExpiredCustomerIds are used to compute the expired passengerIds ie non active passenger who had requested before
The method /getPassengerIdsForExpiryCheck sends back all the passsengerIDs with the score->last upadted Time stamp(Refer to /addPassenger method) to be computed for expiry on client(passenger side)
The method /postDelExpiredCustomerIds recives back a array of all the expired ids to be removed and sets lastChecked=true which is used to prevent rechecking of the passenger ids array for expiry with 5 mins(preventing useless computation) 
Process:
Client(passeneger)-requests->/getPassengerIdsForExpiryCheck->checks&sends back expired ids->/postDelExpiredCustomerIds->deletes the expired ids and sets lastChecked to true preventing rechecks for 5 mins
This process of computing the validty of passengerIds on the path amongst the passengers themselves prevents execcsive load on the serverto keep track and delete ids
as these passenger end of application work as remote computing device. 
*/  
app.get("/getPassengerIdsForExpiryCheck",async(req,res)=>{
const { routeId, startHubId } = req.query;

    if (!routeId || !startHubId ) {
      return res.status(400).send({ error: "Missing routeId, hubId, or driverId" });
    }
   const key=routeId+":"+startHubId+":passenger";
   const lastChecked=redisClient.get(key+":lastChecked");
   if(lastChecked==='true'){
    return;
   }
   else{
    const results = await redisClient.zRangeWithScores(key, 0, -1);
    console.log("result sent", results);

    // Convert array of { value, score } to array of driver objects
    const passengerIds = results.map(({ value, score }) => ({
    passengerId: value,
    score: score,
    }));

    
    // Return JSON array of driver objects
    return res.status(200).json(passengerIds);
   }

});

app.post("/postDelExpiredCustomerIds", async (req, res) => {
  const { routeId, startHubId } = req.query;

  if (!routeId || !startHubId) {
    return res.status(400).send({ error: "Missing routeId or hubId" });
  }

  const key = `${routeId}:${startHubId}:passenger`;

 
  await redisClient.set(`${key}:lastChecked`, 'true', { EX: 5 * 60 });

  const driverIds = req.body;

  if (!Array.isArray(driverIds)) {
    return res.status(400).json({ error: "Expected an array of driver IDs in request body" });
  }

  try {
    if (driverIds.length > 0) {
      await redisClient.zRem(key, ...driverIds);
      console.log("Removed driver IDs from sorted set:", driverIds);
    }
    return res.status(200).json({ success: true });
  } catch (err) {
    console.error("Error removing driver IDs:", err);
    return res.status(500).json({ error: "Internal Server Error" });
  }
});

server.listen(port, () => {
  console.log(`Server running at http://localhost:${port}`);
});
