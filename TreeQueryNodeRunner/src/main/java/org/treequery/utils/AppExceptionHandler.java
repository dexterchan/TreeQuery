package org.treequery.utils;

import org.treequery.model.Node;
import org.treequery.service.StatusTreeQueryCluster;

import java.util.function.Consumer;

public class AppExceptionHandler {
    public static void feedBackException2Client(Consumer<StatusTreeQueryCluster> statusCallback,
                                         Node node, String message,
                                         StatusTreeQueryCluster.QueryTypeEnum queryTypeEnum){
        statusCallback.accept(
                StatusTreeQueryCluster.builder()
                        .node(node)
                        .status(queryTypeEnum)
                        .description(message)
                        .cluster(node.getCluster())
                        .build()
        );
    }
}
