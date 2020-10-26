package org.treequery.service;

import com.google.common.collect.Lists;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.treequery.beam.BeamPipelineBuilderImpl;
import org.treequery.beam.cache.BeamCacheOutputBuilder;
import org.treequery.beam.cache.CacheInputInterface;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservicestatic.DiscoveryServiceInterface;
import org.treequery.execute.GraphNodePipeline;
import org.treequery.execute.NodePipeline;
import org.treequery.execute.NodeTraverser;
import org.treequery.execute.PipelineBuilderInterface;
import org.treequery.model.Node;
import org.treequery.service.proxy.TreeQueryClusterRunnerProxyInterface;
import org.treequery.utils.AppExceptionHandler;
import org.treequery.utils.AvroSchemaHelper;

import java.util.List;
import java.util.function.Consumer;

@Builder
@Slf4j
public class LocalTreeQueryClusterRunner implements TreeQueryClusterRunner{
    @NonNull final TreeQuerySetting treeQuerySetting;
    @NonNull final BeamCacheOutputBuilder beamCacheOutputBuilder;
    @NonNull final AvroSchemaHelper avroSchemaHelper;
    @NonNull final DiscoveryServiceInterface discoveryServiceInterface;
    @NonNull final CacheInputInterface cacheInputInterface;

    @Override
    public void runQueryTreeNetwork(Node node, Consumer<StatusTreeQueryCluster> statusCallback) {
        //Apache Beam pipeline runner creation
        PipelineBuilderInterface pipelineBuilderInterface =  BeamPipelineBuilderImpl.builder()
                .beamCacheOutputInterface(beamCacheOutputBuilder.createBeamCacheOutputImpl())
                .avroSchemaHelper(avroSchemaHelper)
                .discoveryServiceInterface(discoveryServiceInterface)
                .cacheInputInterface(cacheInputInterface)
                .treeQuerySetting(treeQuerySetting)
                .build();

        //Inject Apache Beam pipeline runner
        NodePipeline nodePipeline = GraphNodePipeline.builder()
                .cluster(node.getCluster())
                .pipelineBuilderInterface(pipelineBuilderInterface)
                .cacheTypeEnum(treeQuerySetting.getCacheTypeEnum())
                .avroSchemaHelper(avroSchemaHelper)
                .build();
        List<Node> traversedResult = Lists.newLinkedList();
        NodeTraverser.postOrderTraversalExecution(node, null, traversedResult,nodePipeline );
        nodePipeline.getPipelineBuilder();

        //Execeute the Pipeline runner
        try {
            log.debug("Run the pipeline");
            pipelineBuilderInterface.executePipeline();
            log.debug("Finished the pipeline");

            statusCallback.accept(
                    StatusTreeQueryCluster.builder()
                            .node(node)
                            .status(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS)
                            .description("Finished:"+node.getName())
                            .cluster(node.getCluster())
                            .build()
            );

        }catch(Throwable ex){
            log.error(ex.getMessage());
            AppExceptionHandler.feedBackException2Client(
                    statusCallback, node,
                    ex,
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
