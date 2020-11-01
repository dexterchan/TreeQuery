package org.treequery.utils;

import org.treequery.model.Node;
import org.treequery.service.StatusTreeQueryCluster;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.function.Consumer;

public class AppExceptionHandler {
    public static void feedBackException2Client(Consumer<StatusTreeQueryCluster> statusCallback,
                                         Node node, Throwable throwable,
                                         StatusTreeQueryCluster.QueryTypeEnum queryTypeEnum){

        statusCallback.accept(
                StatusTreeQueryCluster.builder()
                        .node(node)
                        .status(queryTypeEnum)
                        .description(getStackTrace(throwable))
                        .cluster(node.getCluster())
                        .build()
        );
    }

    public static String getStackTrace(Throwable throwable){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
