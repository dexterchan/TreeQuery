package org.treequery.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import org.treequery.model.ActionTypeEnum;
import org.treequery.model.Node;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DummyNode extends Node {


    /*
    public void setDescription(String description) {
        this.description = description;
    }

    public void setAction(ActionTypeEnum action) {
        this.action = action;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }*/

    public static void createMockBehavior(NodeFactory nodeFactory){
        when (nodeFactory.nodeFactoryMethod(any(JsonNode.class))).then(
                invocation -> {
                    JsonNode jsonNode = invocation.getArgument(0);
                    DummyNode node = new DummyNode();
                    node.setDescription(jsonNode.get("description").asText());
                    node.setAction(ActionTypeEnum.valueOf(jsonNode.get("action").asText()));
                    node.setCluster(Cluster.builder()
                            .clusterName(jsonNode.get("cluster").asText())
                            .build());
                    return node;
                }
        );
    }
}
