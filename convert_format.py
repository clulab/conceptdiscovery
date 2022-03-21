import json

new_format={}
nodes_list=[]
edges=[]
# Opening JSON file
with open('sample_graph.json', 'r') as openfile:
    # Reading from json file
    json_dict= json.load(openfile)

for k,v in json_dict.items():
    if k=="nodes":
        id_counter=0
        for node in v:
            nodes_new={}
            nodes_new["id"]=id_counter
            nodes_new["text"] = node["id"]
            nodes_list.append(nodes_new)
            id_counter=id_counter+1



# Serializing json
json_object = json.dumps(new_format, indent=4)

# Writing to sample.json
with open("sre_v2.json", "w") as outfile:
    outfile.write(json_object)
