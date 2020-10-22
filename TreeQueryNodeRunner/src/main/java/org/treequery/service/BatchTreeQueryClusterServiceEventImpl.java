package org.treequery.service;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.treequery.Flow.TreeNodeEventSubscriber;
import org.treequery.cluster.Cluster;
import org.treequery.cluster.ClusterDependencyGraph;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservicestatic.DiscoveryServiceInterface;
import org.treequery.dto.TreeNodeEvent;
import org.treequery.model.Node;
import org.treequery.model.QueryTypeEnum;
import org.treequery.utils.AppExceptionHandler;
import org.treequery.utils.EventBus.TreeNodeEventBusTrafficLight;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

@Slf4j
public class BatchTreeQueryClusterServiceEventImpl implements TreeQueryClusterService {
    private final TreeQuerySetting treeQuerySetting;
    private final TreeNodeEventBusTrafficLight<Node> eventTreeNodeEventBusTrafficLight;

    private final TreeQueryClusterRunner localTreeQueryClusterRunner;
    private final TreeQueryClusterRunner remoteTreeQueryClusterRunner;

    @NonNull private final DiscoveryServiceInterface discoveryServiceInterface;

    private SubmissionPublisher<TreeNodeEvent> localPublisher;
    private SubmissionPublisher<TreeNodeEvent> remotePublisher;



    @Builder
    BatchTreeQueryClusterServiceEventImpl(TreeQuerySetting treeQuerySetting,
                                                 TreeNodeEventBusTrafficLight<Node> eventTreeNodeEventBusTrafficLight,
                                                 TreeQueryClusterRunner localTreeQueryClusterRunner,
                                                 TreeQueryClusterRunner remoteTreeQueryClusterRunner,
                                          DiscoveryServiceInterface discoveryServiceInterface){
        this.treeQuerySetting = treeQuerySetting;
        this.eventTreeNodeEventBusTrafficLight = eventTreeNodeEventBusTrafficLight;
        this.localTreeQueryClusterRunner = localTreeQueryClusterRunner;
        this.remoteTreeQueryClusterRunner = remoteTreeQueryClusterRunner;
        this.discoveryServiceInterface = discoveryServiceInterface;
        this.localPublisher = new SubmissionPublisher<>();
        this.remotePublisher = new SubmissionPublisher<>();
        initPublisher(this.localPublisher, this.eventTreeNodeEventBusTrafficLight, localTreeQueryClusterRunner);
        initPublisher(this.remotePublisher, this.eventTreeNodeEventBusTrafficLight, remoteTreeQueryClusterRunner);

    }

    private static void initPublisher(
            SubmissionPublisher<TreeNodeEvent> publisher,
            TreeNodeEventBusTrafficLight<?> treeNodeEventBusTrafficLight,
            TreeQueryClusterRunner treeQueryClusterRunner){
        Flow.Subscriber<TreeNodeEvent> localSubscriber = TreeNodeEventSubscriber.builder()
                .treeNodeEventBusTrafficLight(treeNodeEventBusTrafficLight)
                .treeQueryClusterRunner(treeQueryClusterRunner)
                .build();
        publisher.subscribe(localSubscriber);
    }

    @Override
    public String runQueryTreeNetwork(Node runNode, Consumer<StatusTreeQueryCluster> StatusCallback) {
        ClusterDependencyGraph clusterDependencyGraph = ClusterDependencyGraph.createClusterDependencyGraph(runNode);


        String uniqueId = iterateNodeListToPublishEventsHelper(
             StatusCallback, clusterDependencyGraph, runNode,
                Optional.empty()
        );



        return uniqueId;
    }
    String  iterateNodeListToPublishEventsHelper(
            Consumer<StatusTreeQueryCluster> StatusCallback,
        ClusterDependencyGraph clusterDependencyGraph ,
        Node rootNode, Optional<StatusTreeQueryCluster> statusTreeQueryCluster
    ){
        String uniqueId = UUID.randomUUID().toString();
        Cluster atCluster = treeQuerySetting.getCluster();

        List<Node> nodeList = clusterDependencyGraph.popClusterWithoutDependency();

        Map<Node, String> registerResult = eventTreeNodeEventBusTrafficLight.queueUp(
                uniqueId, nodeList,
                (statusTreeQueryClusterCallback) -> {
                    BatchTreeQueryClusterServiceEventImpl.this
                            .iterateNodeListToPublishEventsHelper(
                                    StatusCallback, clusterDependencyGraph, rootNode,
                                    Optional.of(statusTreeQueryClusterCallback)
                            );
                }
        );
        if (nodeList.size() == 0){
            statusTreeQueryCluster.ifPresentOrElse(
                    statusTreeQueryClusterResult -> {
                        Node node = statusTreeQueryClusterResult.getNode();
                        String identifier = node.getIdentifier();
                        if (statusTreeQueryClusterResult.getStatus() == StatusTreeQueryCluster.QueryTypeEnum.SUCCESS){
                            discoveryServiceInterface.registerCacheResult(identifier, node.getCluster());
                        }
                        StatusCallback.accept(statusTreeQueryClusterResult);
                    },
                    ()->{
                        AppExceptionHandler.feedBackException2Client(
                                StatusCallback, rootNode,
                                "Fail to get any result",
                                StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR
                        );
                    }
            );

        }

        nodeList.forEach(
                node -> {
                    TreeNodeEvent.TreeNodeEventBuilder treeNodeEventBuilder = TreeNodeEvent.builder()
                            .calcNode(node)
                            .StatusCallback(StatusCallback)
                            .clusterDependencyGraph(clusterDependencyGraph)
                            .rootNode(rootNode)
                            .id(registerResult.get(node));

                    publishTreeNodeEvent(atCluster, node, treeNodeEventBuilder.build(),
                            BatchTreeQueryClusterServiceEventImpl.this.localPublisher,
                            BatchTreeQueryClusterServiceEventImpl.this.remotePublisher);
                }
        );
        return uniqueId;
    }

    static void publishTreeNodeEvent(Cluster atCluster, Node node, TreeNodeEvent treeNodeEvent,
                                     SubmissionPublisher<TreeNodeEvent> localPublisher,
                                     SubmissionPublisher<TreeNodeEvent> remotePublisher){
        if (atCluster.equals(node.getCluster())){
            //log.debug(String.format("Local run: %s", node.toJson()));
            localPublisher.submit(treeNodeEvent);
        }else{
            //log.debug(String.format("Remote run: %s", node.toJson()));
            remotePublisher.submit(treeNodeEvent);
        }
    }


}
