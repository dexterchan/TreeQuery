package org.treequery.service;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.treequery.exception.FailClusterRunException;
import org.treequery.model.Node;
import org.treequery.service.proxy.TreeQueryClusterRunnerProxyInterface;
import org.treequery.utils.AppExceptionHandler;
import org.treequery.utils.ExponentialBackOffHelper;

import java.util.function.Consumer;

@Slf4j
public class RemoteProxyTreeQueryClusterRunner implements TreeQueryClusterRunner{
    static int MAX_RETRIES = 3;
    static int INITIAL_INTERVAL = 2*1000;
    static double MULTIPLIER = 1.1;

    private final TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface;
    private ExponentialBackOffHelper<Node, StatusTreeQueryCluster> exponentialBackOffHelper;

    @Builder
    public RemoteProxyTreeQueryClusterRunner(TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface){
        this.treeQueryClusterRunnerProxyInterface = treeQueryClusterRunnerProxyInterface;
        exponentialBackOffHelper =
                ExponentialBackOffHelper.<Node, StatusTreeQueryCluster>builder()
                        .INITIAL_INTERVAL(INITIAL_INTERVAL)
                        .MAX_RETRIES(MAX_RETRIES)
                        .MULTIPLIER(MULTIPLIER).build();
    }


    @Override
    public void runQueryTreeNetwork(Node node, Consumer<StatusTreeQueryCluster> statusCallback) {

        try {
            exponentialBackOffHelper.backOffCall("remoteCall",
                    (nodeRun) -> {
                        treeQueryClusterRunnerProxyInterface.runQueryTreeNetwork(
                                node, statusCallback
                        );
                        return null;
                    }, node
            );
        }catch(FailClusterRunException fe){
            log.error(fe.getMessage());
            AppExceptionHandler.feedBackException2Client(
                    statusCallback, node,
                    fe,
                    StatusTreeQueryCluster.QueryTypeEnum.FAIL
            );
        }
    }

    @Override
    public void setTreeQueryClusterRunnerProxyInterface(TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface) {
        log.debug("No action");
        //throw new NoSuchMethodError("Not supported for new LocalTreeQueryClusterRunner");
    }
}
