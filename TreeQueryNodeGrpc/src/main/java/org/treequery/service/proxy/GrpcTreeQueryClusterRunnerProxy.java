package org.treequery.service.proxy;

import com.google.common.collect.Maps;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.treequery.cluster.Cluster;
import org.treequery.discoveryservicestatic.DiscoveryServiceInterface;
import org.treequery.discoveryservicestatic.model.Location;
import org.treequery.grpc.client.TreeQueryClient;
import org.treequery.grpc.exception.NoLocationFoundForClusterException;
import org.treequery.grpc.model.TreeQueryResult;
import org.treequery.model.Node;
import org.treequery.proto.TreeQueryRequest;
import org.treequery.service.StatusTreeQueryCluster;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
@Slf4j
public class GrpcTreeQueryClusterRunnerProxy implements TreeQueryClusterRunnerProxyInterface {
    private final TreeQueryRequest.RunMode runMode;
    @NonNull
    private final DiscoveryServiceInterface discoveryServiceInterface;
    private final boolean renewCache;
    private final Map<String, TreeQueryClient> treeQueryClientMap = Maps.newConcurrentMap();

    @Builder
    GrpcTreeQueryClusterRunnerProxy(TreeQueryRequest.RunMode runMode,
                                    DiscoveryServiceInterface discoveryServiceInterface,
                                    boolean renewCache){
        this.runMode = runMode;
        this.discoveryServiceInterface = discoveryServiceInterface;
        this.renewCache = renewCache;
    }

    @Override
    public void runQueryTreeNetwork(Node node, Consumer<StatusTreeQueryCluster> StatusCallback) {
        final Cluster cluster = node.getCluster();

        Location location = Optional.ofNullable(discoveryServiceInterface.getClusterLocation(cluster))
                .orElseThrow(() -> new NoLocationFoundForClusterException(cluster));
        String key = location.toString();
        if (treeQueryClientMap.get(key) == null) {
            synchronized (treeQueryClientMap) {
                if (treeQueryClientMap.get(key) == null) {
                    TreeQueryClient treeQueryClient = new TreeQueryClient(location.getAddress(), location.getPort());
                    treeQueryClientMap.put(key, treeQueryClient);
                }
            }
        }
        TreeQueryClient treeQueryClient = treeQueryClientMap.get(key);
        log.info(String.format("Connecting to Cluster %s GRPC server at %s:%d",cluster, treeQueryClient.getHost(), treeQueryClient.getPort()));
        TreeQueryResult treeQueryResult = treeQueryClient.query(runMode, node.toJson(),renewCache,1,1 );
        StatusTreeQueryCluster.QueryTypeEnum queryTypeEnum;
        if (!treeQueryResult.getHeader().isSuccess()){
            queryTypeEnum = StatusTreeQueryCluster.QueryTypeEnum.FAIL;
        }else{
            queryTypeEnum = StatusTreeQueryCluster.QueryTypeEnum.SUCCESS;
        }
        StatusCallback.accept(
                StatusTreeQueryCluster.builder()
                        .status(queryTypeEnum)
                        .description(String.format("%d:%s",
                                treeQueryResult.getHeader().getErr_code(),
                                treeQueryResult.getHeader().getErr_msg()))
                        .node(node)
                        .cluster(cluster)
                .build()
        );
    }

}
