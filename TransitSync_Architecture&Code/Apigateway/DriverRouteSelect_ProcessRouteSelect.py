import boto3
import hashlib
import json
from boto3.dynamodb.conditions import Attr
import redis
from typing import Dict, Any
import time
from neo4j import GraphDatabase
dynamodb = boto3.resource('dynamodb')
route_table = dynamodb.Table('Route')
driver_table = dynamodb.Table('Driver')
hub_table = dynamodb.Table('Hub')
redis_client = redis.Redis(
    host='REDIS_HOST',
    port="PORT_NO",
    decode_responses=True,
    username="default",
    password="PASSWORD",
)
NEO4J_URI = "neo4jURI"
NEO4J_USER = "neo4j"
NEO4J_PASSWORD = "PASSWORD"
driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))

#use redisPipeline where ever possible
def lambda_handler(event, context):
    # Extract data from body
     # Parse the body if it's a string
    try:
        body = json.loads(event['body']) if isinstance(event['body'], str) else event['body']
    except Exception as e:
        return {
            'statusCode': 400,
            'body': json.dumps(f"Invalid JSON body: {str(e)}")
        }

    print("Received event.body:", json.dumps(body, indent=2))
    driver_id = body['driverId']
    email_id = body['email']
    polyline = body['routePolyline']
    cost = body['price']
    start_station_id = body['startHubId']
    end_station_id = body['endHubId']
    start_station_name = body['startHubName']
    end_station_name = body['endHubName']
    start_hub_LtLong = body['startHubLatLng']
    end_hub_LtLong = body['endHubLatLng']
    start_hub_city = body['startCity']
    end_hub_city = body['endCity']
    distance = body['distanceBtwHub']
    vehicle_No=body['vehicleNO']

    # Check if Driver exists
    driver_resp = driver_table.get_item(Key={'Driver_ID': driver_id})
    if 'Item' not in driver_resp:
        return {
            'statusCode': 400,
            'body': json.dumps(f"Driver with ID {driver_id} does not exist.")
        }

    # Check if route already exists in RouteSelect and is identical
    driver_item = driver_resp['Item']
    existing_routes = driver_item.get('RouteSelect', {})

    # Create Route_ID by hashing the polyline
    route_id = hashlib.sha256(polyline.encode('utf-8')).hexdigest()[:32]

    if route_id in existing_routes:
        existing_cost, existing_polyline = existing_routes[route_id]
        if str(existing_cost) == str(cost) and existing_polyline == polyline:
            return {
                'statusCode': 200,
                'body': json.dumps(f"Route already exists for driver {driver_id}. No update performed.")
            }



    # Check and insert start station hub if not exists
    start_hub_resp = hub_table.get_item(Key={'Hub_ID': start_station_id})
    if 'Item' not in start_hub_resp:
        add_Hub(
            start_hub_city,
            start_station_id,
            start_station_name,
            1,
            {},
            start_hub_LtLong
        )
    # Check and insert end station hub if not exists
    end_hub_resp = hub_table.get_item(Key={'Hub_ID': end_station_id})
    if 'Item' not in end_hub_resp:
        add_Hub(
            end_hub_city,
            end_station_id,
            end_station_name,
            1,
            {},#routeId:{endStation}
            end_hub_LtLong
        )

    

    # Check if Route_ID already exists in Route table
    existing_route = route_table.get_item(Key={'Route_ID': route_id})
    route_exists = 'Item' in existing_route
    
    if not route_exists:
        # Insert new route
        route_table.put_item(Item={
            'Route_ID': route_id,
            'Cost': cost,
            'Distance': distance,
            'Polyline': polyline,
            'Start_Station': start_station_name,
            'End_Station': end_station_name,
            'Mid_Station': 'none'
        })
        #move to stagging area
        distance_neo4j = distance.split(" ")[0]
        print("Reached in cypher")
        try:
            with driver.session() as session:
                 result = session.write_transaction(
                     insert_road,
                     start_station_name,          # $start_name
                     end_station_name,            # $end_name
                     distance_neo4j,              # $distance
                     cost,                        # $price
                     start_hub_LtLong,            # $start_hub_latlng
                     end_hub_LtLong               # $end_hub_latlng
                 )
                 print("Cypher execution result:", result)
            
        except Exception as e:
             print("Error executing Cypher query:", e)

        print("reach 1")
        hubs_route_key_1 = f"{start_station_id}:{end_station_id}"
        hubs_route_key_2 = f"{end_station_id}:{start_station_id}"
        print("reach 2")
        redis_client.zadd(hubs_route_key_1, {polyline: 1})
        redis_client.zadd(hubs_route_key_2, {polyline: 1})
        redis_client.hset(route_id, mapping={
        'polyline': polyline,
        'cost': cost,
	    'startHubId':start_station_id,
	    'endHubId':end_station_id,
	    'startHubName':start_station_name,
	    'endHubName':end_station_name,
	    'startHubLatLng':start_hub_LtLong,
	    'endHubLatLng':end_hub_LtLong,
	    'startCity':start_hub_city,
	    'endCity':end_hub_city,
	    'distanceBtwHub':distance
        })
        print("reached here")
        # Update start hub's Routes_Connected with route
        hub_table.update_item(
            Key={'Hub_ID': start_station_id},
            UpdateExpression="SET Routes_Connected.#rid = :end_station",
            ExpressionAttributeNames={
                "#rid": route_id
            },
            ExpressionAttributeValues={
                ":end_station": end_station_name
            }
        )
        print("insertion done")


    # Update RouteSelect and Email
    driver_table.update_item(
        Key={'Driver_ID': driver_id},
        UpdateExpression="SET RouteSelect.#rid = :rinfo, Email_ID = :email",
        ExpressionAttributeNames={
            "#rid": route_id
        },
        ExpressionAttributeValues={
            ":rinfo": [cost, polyline],
            ":email": email_id
        }
    )

    return {
        'statusCode': 200,
        'body': json.dumps(f"Processed Route_ID: {route_id}")
    }







# Don't Touch this--------------------------------------------------------------------------------------------------Important
# Update connection parameters as needed for your Lambda environment

#if Post method
#def redisAndDynamo_Update_Data(body, context):
#Write here, Extract the values from body , get hubId city etc call functions 
#also call dynamo Db funcgtion from here
#call add_Hub and other dynamoDb
def insert_road(tx,
                start_name: str,
                end_name: str,
                distance: float,
                price: float,
                start_hub_latlng: str,
                end_hub_latlng: str):
    query = """
    MERGE (start:Location {name: $start_name})
      ON CREATE SET start.hubLatLng = $start_hub_latlng
      ON MATCH  SET start.hubLatLng = $start_hub_latlng

    MERGE (end:Location {name: $end_name})
      ON CREATE SET end.hubLatLng = $end_hub_latlng
      ON MATCH  SET end.hubLatLng = $end_hub_latlng

    MERGE (start)-[:ROAD {distance: $distance, price: $price}]->(end)
    MERGE (end)  -[:ROAD {distance: $distance, price: $price}]->(start)
    """
    return tx.run(
        query,
        start_name=start_name,
        end_name=end_name,
        distance=float(distance),
        price=float(price),
        start_hub_latlng=start_hub_latlng,
        end_hub_latlng=end_hub_latlng
    ).consume()


def add_Hub(city: str, Hub_Id: str, name: str, score: float, Routes_Connected: Dict[str, str], LatLng: str) -> None:#also get startAnd end latlong
    """
    Add a restaurant to the autocomplete index.
    
    Args:
        city: City name to associate with this HUB 
        Hub_Id: Unique identifier for the HUB: PlacesId from Google
        name: HUB name
        score: Ranking score (higher = more prominent in results):Not required Right now
        details: Optional dictionary of restaurant details:All the vales that needed to be stroed in hashMap/drectory Like
         r.hset("hub_info", Hub_Id, json.dumps({
        "name": name,
        "city": city,
        "lat": lat,
        "lon": lon
    }))
    """
    try:
        try:
            lat_str, long_str = LatLng.split(',')
            latitude = float(lat_str.strip())
            longitude = float(long_str.strip())
        except Exception as e:
            raise ValueError(f"Invalid LatLong format: '{LatLng}'. Expected 'lat,lon'. Error: {e}")
    
        print("Hub Name:", name)
        geoaddKey = name+":"+Hub_Id
        redis_client.geoadd(f"{city}:Hub", (longitude, latitude, geoaddKey)) #geosddKey=hubName:HubId
        redis_client.rpush("cities", city)
        # Set up autocomplete key
        autocomplete_key = f"{city}:autocomplete"
        
        # OPTIMIZATION: Limit prefix generation to prevent timeouts
        # Use a more reasonable prefix range (3 to 8 characters)
        # max_prefix_length = min(15, len(name))
        # prefix_values = {}
        
        # # Prepare all prefixes in a single dictionary for batch upload
        # for i in range(3, max_prefix_length + 1):
        #     prefix = name[:i].lower()
        #     value = f"{prefix}:{name}:{Hub_Id}"
        #     prefix_values[value] = score
        
        # # Add all prefixes in a single ZADD operation
        # if prefix_values:
        #     redis_client.zadd(autocomplete_key, prefix_values)
        prefix=name.lower()
        value=f"{prefix}:{name}:{Hub_Id}"
        redis_client.zadd(autocomplete_key, {value: score})
   
        # Store full Hub details in Redis hash
        redis_client.hset(f"Hub:{Hub_Id}", mapping={
            'name': name,
            'city': city,
            'LatLng': LatLng,
            'id': Hub_Id
        })
        #HubId1:HubId2 ---->polyLines array
        # Add to DynamoDB
        hub_table.put_item(Item={
            'Hub_ID': Hub_Id,
            'Hub_Name': name,
            'Hub_City': city,
            'Hub_LatLong': LatLng,  # Store as string in Dynamo
            'Routes_Connected': Routes_Connected or {}
        })
    except Exception as e:
        return{
            'statusCode': 500,
            'body': json.dumps(f"Something went wrong: {e}")
        }