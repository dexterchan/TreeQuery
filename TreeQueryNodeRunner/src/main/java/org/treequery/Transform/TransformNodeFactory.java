package org.treequery.Transform;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import org.treequery.Transform.function.JoinFunction;
import org.treequery.cluster.NodeFactory;
import org.treequery.model.ActionTypeEnum;
import org.treequery.model.JoinAble;
import org.treequery.model.Node;

import java.util.List;
import java.util.Optional;

public class TransformNodeFactory implements NodeFactory {

    @Override
    public Node nodeFactoryMethod(JsonNode jsonNode) {
        ActionTypeEnum actionTypeEnum = ActionTypeEnum.valueOf(jsonNode.get("action").asText());
        Node node = null;
        switch(actionTypeEnum){
            case FLATTEN:
                node = createFlattenNode(jsonNode);
                break;
            case INNER_JOIN:
                node = createJoinNode(jsonNode);
                break;
            case LOAD:
                node = createLoadNode(jsonNode);
                break;
            case QUERY:
                node = createQueryNode(jsonNode);
                break;
        }

        return node;
    }



    static Node createFlattenNode(JsonNode jsonNode){
        FlattenNode flattenNode = new FlattenNode();
        flattenNode.setBasicValue(jsonNode);
        return flattenNode;
    }
    static Node createJoinNode(JsonNode jsonNode) {
        JoinAble joinAble = JoinFunction.builder().jsonNode(jsonNode).build();
        JoinNode joinNode = new JoinNode(joinAble);
        joinNode.setBasicValue(jsonNode);
        return joinNode;
    }

    static Node createLoadNode(JsonNode jsonNode){
        LoadLeafNode loadLeafNode = null;
        LoadLeafNode.LoadLeafNodeBuilder loadLeafNodeBuilder = LoadLeafNode.builder();

        String source = Optional.ofNullable(jsonNode.get("source")).orElseThrow(()->new IllegalArgumentException("Missing source in Load Node")).asText();
        String avroSchema = Optional.ofNullable(jsonNode.get("avro_schema")).orElseThrow(()->new IllegalArgumentException("Missing avro_schema in Load Node")).asText();
        loadLeafNodeBuilder.source(source);
        loadLeafNodeBuilder.avro_schema(avroSchema);
        loadLeafNode = loadLeafNodeBuilder.build();
        loadLeafNode.setBasicValue(jsonNode);
        return loadLeafNode;
    }

    static Node createQueryNode(JsonNode jsonNode){
        NodeFactory dataSourceFactory = new QueryableDataSourceFactory();
        return dataSourceFactory.nodeFactoryMethod(jsonNode);
    }
}
