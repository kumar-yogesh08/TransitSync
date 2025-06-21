import boto3
import json
import redis
import os
from urllib.parse import unquote

redis_client = redis.Redis(
    host='REDIS_HOST',
    port="PORT_NO",
    decode_responses=True,
    username="default",
    password="PASSWORD",
)

def lambda_handler(event, context):
    #If parameters has geosearch = LatLng AND also the city, then geosearch within 3 km radius
    #If query parameter is autocomplete = prefix then use the following snippet:-
    '''
        range_results = redis_client.zrangebylex(
        key,
        f"[{prefix_lower}",  # Lower bound: entries starting with prefix
        f"[{prefix_lower}\xff",  # Upper bound: prefix + highest byte
        start=0,
        num=count
    )'''
    #If query parameter = HubId then hget(HubId)
    #If query parameter = RoutesId then return the sorted set HubId1:HubId2 which is RoutesId (directly put)
    #If query parameter = RouteId then return L at 0 where L is list
    print("Full event:", json.dumps(event))
    try:
        # Test Redis connection
        redis_client.ping()
        print("Successfully connected to Redis")
    except redis.exceptions.ConnectionError as e:
        error_message = f"Error connecting to Redis: {e}"
        print(error_message)
        return {
            'statusCode': 500,
            'body': json.dumps({'error': error_message})
        }

    parameters = event.get('queryStringParameters', {})
    print(parameters)
    if not parameters:
        return {
            'statusCode': 400,
            'body': json.dumps({'error': 'No query parameters provided'})
        }

    try:
        if 'cities' in parameters:
            cities = redis_client.lrange("cities", 0, -1)
            return {
            'statusCode': 200,
            'body': json.dumps({'cities': cities})
            }
        if 'routeId' in parameters:
            route_id = parameters['routeId']
            route_data = redis_client.hgetall(route_id)
            print("routeData:",route_data)
            # # Decode bytes to string
            # decoded_data = {k.decode(): v.decode() for k, v in route_data.items()}

            return {
            'statusCode': 200,
            'body': json.dumps(route_data)
            }

        if 'city' in parameters:
            city = parameters['city']
        if 'geoSearch' in parameters and city!=None and 'radius' in parameters:
            # Example:  geosearch=12.34,56.78&city=MyCity
            encoded_coordinates = parameters['geoSearch']
            decoded_coordinates = unquote(encoded_coordinates)
            lat, lng = decoded_coordinates.split(',')
            radius=parameters['radius']
            print(f'Lat:{lat} and Long:{lng} and radius:{radius}')
            
            city = parameters['city']
            

            # Use GEOSEARCH to find places near the given coordinates
            geosearchkey = f"{city}:Hub"
            results = redis_client.geosearch(
                name=geosearchkey, # Or a key relevant to the city
                latitude=float(lat),
                longitude=float(lng),
                radius=radius,
                unit="m", # Or km
                withdist=True,
                withcoord=True

            )
            if results:
                # Format the results
                formatted_results = [{"hubName:HubId": member[0], "latLng": {"lat": member[2][1], "lng": member[2][0]}, "distance": member[1]} for member in results]
                return {
                    'statusCode': 200,
                    'body': json.dumps(formatted_results)
                }
            else:
                return{
                    'statusCode': 404,
                    'body': json.dumps({'message': "Geosearch : No results found"})
                }


        elif 'autocomplete' in parameters and city!=None:
            prefix = parameters['autocomplete']
            count = 10  # Default count is 10
            prefix_lower = prefix.lower()
            autocompletekey = f"{city}:autocomplete" #Or some other key

            # Autocomplete functionality using zrangebylex
            range_results = redis_client.zrangebylex(
                autocompletekey,
                f"[{prefix_lower}",
                f"[{prefix_lower}\xff",
                start=0,
                num=count
            )
            return {
                'statusCode': 200,
                'body': json.dumps(range_results)
            }

        elif 'hubId' in parameters:
            hub_id = parameters['hubId']
            #city=parameters['city']
            hubsearchkey = f"Hub:{hub_id}"
            #  Fetch hub details using HGET
            hub_data = redis_client.hgetall(hubsearchkey) # Assuming "hubs" is the key
            if hub_data:
                return {
                    'statusCode': 200,
                    'body': json.dumps(hub_data)
                }
            else:
                 return {
                    'statusCode': 404,
                    'body': json.dumps({'error': "HubId not found"})
                }

        elif 'routesId' in parameters:
            routes_id = parameters['routesId']  # e.g., "Hub1:Hub2"
            #  Fetch route details using ZRANGE
            route_data = redis_client.zrange(routes_id, 0, -1)  # Directly use RoutesId as key
            return {
                'statusCode': 200,
                'body': json.dumps(route_data)
            }

        # elif 'RouteId' in parameters:
        #     route_id = parameters['RouteId']
        #     #  Fetch route details using LRANGE
        #     route_data = redis_client.lrange(route_id, 0, 0)  # Fetch element at index 0
        #     if route_data:
        #         return {
        #             'statusCode': 200,
        #             'body': json.dumps(route_data[0])  # Return only the first element
        #         }
        #     else:
        #         return {
        #             'statusCode': 404,
        #             'body': json.dumps({'error': "RouteId not found"})
        #         }

        else:
            return {
                'statusCode': 400,
                'body': json.dumps({'error': 'Invalid query parameters'})
            }
    except Exception as e:
        error_message = f"An error occurred: {e}"
        print(error_message)
        return {
            'statusCode': 500,
            'body': json.dumps({'error': error_message})
        }

























































'''
#implement
#if get method

async def redisAndDynamoDB_Get_Data(city: str, prefix: str, count: int = 10) -> List[Dict[str, Any]]:
    """
    Query for autocomplete suggestions.
    
    Args:
        city: City to search in
        prefix: Text prefix to match
        count: Maximum number of results to return
        
    Returns:
        All get values either from dynamo or redis should be fetched from  this function
    """
    
    #if Qery==>autoComplete=City=city&Prefix=prefix
    autoCompleteGet(city, prefix)#it will be sent from the client side max len of 5 min len of 3 even though the user types full [0:3],[0:4],[0:5] as prefix stored as such
    #if Qery==>GetHubInfo=Hub_Id
    getHubInfo(Hub_Id)


def autoCompleteGet(city, prefix):
    # city = event['city']
    key = f"{city}:autocomplete"
    prefix_lower = prefix.lower()
    # Find all entries that start with the given prefix
    # Redis lexicographical range query
    range_results = redis_client.zrangebylex(
        key,
        f"[{prefix_lower}",  # Lower bound: entries starting with prefix
        f"[{prefix_lower}\xff",  # Upper bound: prefix + highest byte
        start=0,
        num=count
    )
    return range_results

    # Process results to extract Hub IDs
    # Hub_Ids = []
    # for item in range_results:
    #     parts = item.split(':')
    #     if len(parts) >= 3:
    #         Hub_Ids.append(parts[2])  # Get the ID from prefix:name:id
def getHubInfo(Hub_Id):
    # Fetch full Hub details
    details = redis_client.hgetall(f"Hub:{Hub_Id}")
    return details    
    zget(f"{hub_id1}:{hub_id2}")

    # Fetch full Hub details
    # results = []
    # for Hub_Id in Hub_Ids:
    #     details = redis_client.hgetall(f"Hub:{Hub_Id}")
    #     if details:  # Only add if details were found
    #         results.append(details)
    
    # return results
    '''