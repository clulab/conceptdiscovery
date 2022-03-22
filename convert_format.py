import json

new_format={}
nodes_list=[]
edges_list=[]
# Opening JSON file
nodes_txt_id_mapping={}
with open('data_from_shengtai_pretty_print.json', 'r') as openfile:
    # Reading from json file
    json_dict= json.load(openfile)

for k,v in json_dict.items():
    if k=="nodes":
        id_counter=0
        for old_node in v:
            nodes_new={}
            nodes_new["id"]=str(id_counter)
            nodes_new["text"] = str(old_node["id"])
            nodes_list.append(nodes_new)
            name=old_node["id"]
            nodes_txt_id_mapping[name]=str(id_counter)

            id_counter = id_counter + 1

    if k == "links":
        for old_edge in v:
            edges_new={}
            edges_new["src"]=str(nodes_txt_id_mapping[str(old_edge["source"])])
            edges_new["dst"] = str(nodes_txt_id_mapping[old_edge["target"]])
            edges_new["weight"] = (old_edge["weight"])
            edges_list.append(edges_new)



new_format["nodes"]=nodes_list
new_format["edges"]=edges_list

# Serializing json
json_object = json.dumps(new_format, indent=4)

# Writing to sample.json
with open("shengtai_data_zhengtang_format.json", "w") as outfile:
    outfile.write(json_object)
