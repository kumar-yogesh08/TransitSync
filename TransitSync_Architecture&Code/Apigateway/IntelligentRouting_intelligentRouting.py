import json
from neo4j import GraphDatabase

# Neo4j database connection
URI = "NEO4jURI"
AUTH = ("neo4j", "PASSWORD")

# Cypher query to find shortest path using Dijkstra
query = """
MATCH (start:Location {name: $start}), (end:Location {name: $end})
CALL apoc.algo.dijkstra(start, end, 'ROAD', 'distance')
YIELD path, weight
WITH path, weight, reduce(totalPrice = 0, rel in relationships(path) | totalPrice + rel.price) AS total_price
RETURN path, weight AS total_distance, total_price;
"""

# Query to list all edges (optional)
query2 = """MATCH (u:Location)-[p:ROAD]->(n:Location) RETURN u, p, n"""

def get_shortest_path(start, end):
    response = {}
    with GraphDatabase.driver(URI, auth=AUTH) as driver:
        with driver.session() as session:
            result = session.run(query, start=start, end=end)
            for record in result:
                total_distance = record['total_distance']
                total_price = record['total_price']
                path_nodes = record["path"].nodes

                # Extract name and hubLatLng for each node
                names = [node.get("name", "Unknown") for node in path_nodes]
                hubLatLngs = [node.get("hubLatLng", "") for node in path_nodes]

                response = {
                    "path": names,
                    "hubLatLngs": hubLatLngs,
                    "total_distance": total_distance,
                    "total_price": total_price
                }
                break  # Only return first result
    return response

def get_all_data():
    with GraphDatabase.driver(URI, auth=AUTH) as driver:
        with driver.session() as session:
            result = session.run(query2)
            for record in result:
                node1 = record["u"]["name"]
                node2 = record["n"]["name"]
                distance = record["p"]["distance"]
                print(f"{node1} --({distance} km)--> {node2}")

# Lambda entry point
def lambda_handler(event, context):
    try:
        # Parse event body safely
        body = json.loads(event['body']) if isinstance(event['body'], str) else event['body']
        print("Received event.body:", json.dumps(body, indent=2))

        start = body.get("start")
        end = body.get("end")

        if not start or not end:
            return {
                'statusCode': 400,
                'body': json.dumps({'error': 'Missing "start" or "end" in request body'})
            }

        # Query shortest path from Neo4j
        path_info = get_shortest_path(start, end)

        if not path_info:
            return {
                'statusCode': 404,
                'body': json.dumps({'message': 'No path found between the given locations'})
            }

        return {
            'statusCode': 200,
            'body': json.dumps(path_info)
        }

    except Exception as e:
        print("Error occurred:", str(e))
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }
