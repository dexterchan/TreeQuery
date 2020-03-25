package org.treequery.service;

import com.google.common.collect.Lists;
import org.treequery.beam.BeamPipelineBuilderImpl;
import org.treequery.beam.cache.BeamCacheOutputInterface;
import org.treequery.cluster.ClusterDependencyGraph;
import org.treequery.execute.GraphNodePipeline;
import org.treequery.execute.NodePipeline;
import org.treequery.execute.NodeTraverser;
import org.treequery.execute.PipelineBuilderInterface;
import org.treequery.model.AvroSchemaHelper;
import org.treequery.model.CacheTypeEnum;
import org.treequery.model.Node;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;


import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Builder
public class TreeQueryClusterRunnerImpl implements TreeQueryClusterRunner {
    CacheTypeEnum cacheTypeEnum;
    BeamCacheOutputInterface beamCacheOutputInterface;
    AvroSchemaHelper avroSchemaHelper;

    @Override
    public void runQueryTreeNetwork(Node rootNode, Consumer<StatusTreeQueryCluster> statusCallback) {
        ClusterDependencyGraph clusterDependencyGraph = ClusterDependencyGraph.createClusterDependencyGraph(rootNode);

        while (true){
            List<Node> nodeList = clusterDependencyGraph.findClusterWithoutDependency();
            if (nodeList.size()==0){
                break;
            }
            for (Node node: nodeList) {

                //Apache Beam pipeline runner creation
                PipelineBuilderInterface pipelineBuilderInterface =  BeamPipelineBuilderImpl.builder()
                        .beamCacheOutputInterface(beamCacheOutputInterface)
                        .avroSchemaHelper(avroSchemaHelper)
                        .build();

                //Inject Apache Beam pipeline runner
                NodePipeline nodePipeline = GraphNodePipeline.builder()
                        .cluster(node.getCluster())
                        .pipelineBuilderInterface(pipelineBuilderInterface)
                        .cacheTypeEnum(cacheTypeEnum)
                        .avroSchemaHelper(avroSchemaHelper)
                        .build();
                List<Node> traversedResult = Lists.newLinkedList();
                NodeTraverser.postOrderTraversalExecution(node, null, traversedResult,nodePipeline );
                nodePipeline.getPipelineBuilder();

                //Execeute the Pipeline runner
                try {
                    pipelineBuilderInterface.executePipeline();
                }catch(Exception ex){
                    log.error(ex.getMessage());
                    statusCallback.accept(
                            StatusTreeQueryCluster.builder()
                                    .status(StatusTreeQueryCluster.QueryTypeEnum.FAIL)
                                    .description(ex.getMessage())
                                    .build()
                    );
                    return;
                }
                clusterDependencyGraph.removeClusterDependency(node);
            }
        }

        statusCallback.accept(StatusTreeQueryCluster.builder()
                                .status(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS)
                                .description("OK")
                                .build());
    }
}