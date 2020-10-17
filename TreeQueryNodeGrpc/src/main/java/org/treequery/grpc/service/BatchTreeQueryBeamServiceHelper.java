package org.treequery.grpc.service;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.treequery.beam.cache.BeamCacheOutputBuilder;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservicestatic.DiscoveryServiceInterface;
import org.treequery.exception.CacheNotFoundException;
import org.treequery.exception.TimeOutException;
import org.treequery.grpc.exception.NodeNotMatchingGrpcServiceClusterException;
import org.treequery.service.*;
import org.treequery.service.proxy.TreeQueryClusterRunnerProxyInterface;
import org.treequery.utils.AvroIOHelper;
import org.treequery.utils.AvroSchemaHelper;
import org.treequery.model.Node;
import org.treequery.proto.TreeQueryRequest;
import org.treequery.utils.AsyncRunHelper;
import org.treequery.utils.EventBus.BasicTreeNodeEventBusTrafficLightImpl;
import org.treequery.utils.JsonInstructionHelper;
import org.treequery.beam.cache.CacheInputInterface;

import java.util.function.Consumer;

@Slf4j
public class BatchTreeQueryBeamServiceHelper implements TreeQueryBeamService {
    TreeQueryClusterService treeQueryClusterService;
    @NonNull
    BeamCacheOutputBuilder beamCacheOutputBuilder;
    @NonNull
    AvroSchemaHelper avroSchemaHelper;
    @NonNull
    DiscoveryServiceInterface discoveryServiceInterface;
    @NonNull
    TreeQuerySetting treeQuerySetting;
    @NonNull
    TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface;
    @NonNull
    CacheInputInterface cacheInputInterface;

    @Builder
    public BatchTreeQueryBeamServiceHelper(AvroSchemaHelper avroSchemaHelper,
                                           DiscoveryServiceInterface discoveryServiceInterface,
                                           TreeQuerySetting treeQuerySetting,
                                           TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface,
                                           CacheInputInterface cacheInputInterface){
        this.avroSchemaHelper = avroSchemaHelper;
        this.discoveryServiceInterface = discoveryServiceInterface;
        beamCacheOutputBuilder = BeamCacheOutputBuilder.builder()
                                    .treeQuerySetting(treeQuerySetting).build();
        this.treeQueryClusterRunnerProxyInterface = treeQueryClusterRunnerProxyInterface;
        this.treeQuerySetting = treeQuerySetting;
        this.cacheInputInterface = cacheInputInterface;
        init();
    }

    public void init(){
        TreeQueryClusterRunner localTreeQueryClusterRunner =  LocalTreeQueryClusterRunner.builder()
                .avroSchemaHelper(avroSchemaHelper)
                .beamCacheOutputBuilder(this.beamCacheOutputBuilder)
                .discoveryServiceInterface(discoveryServiceInterface)
                .treeQuerySetting(treeQuerySetting)
                .cacheInputInterface(cacheInputInterface)
                .build();

        TreeQueryClusterRunner remoteTreeQueryClusterRunner =  RemoteProxyTreeQueryClusterRunner.builder()
                .treeQueryClusterRunnerProxyInterface(treeQueryClusterRunnerProxyInterface)
                .build();
        treeQueryClusterService =  BatchTreeQueryClusterServiceEventImpl.builder()
                .localTreeQueryClusterRunner(localTreeQueryClusterRunner)
                .remoteTreeQueryClusterRunner(remoteTreeQueryClusterRunner)
                .treeQuerySetting(treeQuerySetting)
                .discoveryServiceInterface(discoveryServiceInterface)
                .eventTreeNodeEventBusTrafficLight(new BasicTreeNodeEventBusTrafficLightImpl())
        .build();
    }
    @Override
    public PreprocessInput preprocess(String jsonInput){
        Node rootNode;
        Schema outputSchema;
        try {
            rootNode = JsonInstructionHelper.createNode(jsonInput);
            outputSchema = avroSchemaHelper.getAvroSchema(rootNode);
        }catch(Exception je){
            throw new IllegalArgumentException(String.format("Not able to parse:%s", jsonInput));
        }
        if (!rootNode.getCluster().equals(treeQuerySetting.getCluster())){
            NodeNotMatchingGrpcServiceClusterException runtimeException = new NodeNotMatchingGrpcServiceClusterException(rootNode.getCluster(), treeQuerySetting);
            log.error(runtimeException.getMessage());
            throw runtimeException;
        }
        return PreprocessInput.builder()
                .node(rootNode)
                .outputSchema(outputSchema)
                .build();
    }
    @Override
    public ReturnResult runAndPageResult(TreeQueryRequest.RunMode runMode,
                                         PreprocessInput preprocessInput,
                                         boolean renewCache,
                                         long pageSize,
                                         long page,
                                         Consumer<GenericRecord> dataConsumer) {

        String identifier = preprocessInput.getNode().getIdentifier();

        if (!renewCache){
            try{
                Schema schema = AvroIOHelper.getPageRecordFromAvroCache(
                        treeQuerySetting, identifier, pageSize, page,
                         dataConsumer);
                return ReturnResult.builder()
                        .hashCode(identifier)
                        .statusTreeQueryCluster(
                                StatusTreeQueryCluster.builder()
                                        .status(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS)
                                        .description("Fresh from cache")
                                        .node(preprocessInput.getNode())
                                        .cluster(preprocessInput.getNode().getCluster())
                                        .build()
                        )
                        .dataSchema(schema)
                        .build();
            }catch(CacheNotFoundException che){
                log.info(String.format("Cluster:%s Node:%s from cluster %s identifier %s not found, need to rerun",
                        treeQuerySetting.getCluster().toString(),
                        preprocessInput.getNode().getName(),
                        preprocessInput.getNode().getCluster(),
                        identifier));
                return this.runQuery(preprocessInput.getNode(), pageSize, page, dataConsumer);
            }
        }
        return this.runQuery(preprocessInput.getNode(), pageSize, page, dataConsumer);
    }

    private ReturnResult runQuery(Node rootNode, long pageSize, long page, Consumer<GenericRecord> dataConsumer){
        final AsyncRunHelper asyncRunHelper =  AsyncRunHelper.create();
        final String hashCode = rootNode.getIdentifier();
        treeQueryClusterService.runQueryTreeNetwork(rootNode, (status)->{
            asyncRunHelper.continueRun(status);
            log.debug(status.toString());
        });

        try {
            StatusTreeQueryCluster statusTreeQueryCluster = asyncRunHelper.waitFor();
            if(asyncRunHelper.isError()){
                log.error("Failure run with status code:"+statusTreeQueryCluster.getStatus());
                return ReturnResult.builder()
                        .hashCode(hashCode)
                        .statusTreeQueryCluster(statusTreeQueryCluster)
                        .build();
            }else{
                try {
                    Schema schema = AvroIOHelper.getPageRecordFromAvroCache(
                            treeQuerySetting,
                            rootNode.getIdentifier(), pageSize, page, dataConsumer);
                    return ReturnResult.builder()
                            .hashCode(hashCode)
                            .statusTreeQueryCluster(statusTreeQueryCluster)
                            .dataSchema(schema)
                            .build();
                }catch(CacheNotFoundException che){
                    log.error(che.getMessage());
                    return ReturnResult.builder()
                            .hashCode(hashCode)
                            .statusTreeQueryCluster(
                                    StatusTreeQueryCluster.builder()
                                    .status(StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR)
                                    .description(che.getMessage())
                                            .node(rootNode)
                                            .cluster(rootNode.getCluster())
                                    .build()
                            )
                            .build();
                }
            }
        }catch(TimeOutException te){
            log.error(te.getMessage());
            throw new IllegalStateException(String.format("Time out:%s", rootNode.toString()));
        }
    }
}
