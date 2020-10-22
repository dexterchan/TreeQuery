package org.treequery.grpc.service;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.treequery.beam.cache.BeamCacheOutputBuilder;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservicestatic.DiscoveryServiceInterface;
import org.treequery.grpc.utils.TestDataAgent;
import org.treequery.service.LocalTreeQueryClusterRunner;
import org.treequery.service.PreprocessInput;
import org.treequery.service.ReturnResult;
import org.treequery.service.proxy.LocalDummyTreeQueryClusterRunnerProxy;
import org.treequery.service.proxy.TreeQueryClusterRunnerProxyInterface;
import org.treequery.utils.BasicAvroSchemaHelperImpl;
import org.treequery.model.CacheTypeEnum;
import org.treequery.proto.TreeQueryRequest;
import org.treequery.service.StatusTreeQueryCluster;
import org.treequery.utils.AvroSchemaHelper;
import org.treequery.utils.TreeQuerySettingHelper;
import org.treequery.utils.proxy.LocalCacheInputInterfaceProxyFactory;
import org.treequery.beam.cache.CacheInputInterface;
import org.treequery.utils.proxy.CacheInputInterfaceProxyFactory;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@Slf4j
class SimpleBatchTreeQueryBeamServiceHelperTest {
    String jsonString;
    BatchTreeQueryBeamServiceHelper batchTreeQueryBeamServiceHelper;
    DiscoveryServiceInterface discoveryServiceInterface;
    AvroSchemaHelper avroSchemaHelper;
    TreeQuerySetting treeQuerySetting;
    TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface;
    CacheInputInterface cacheInputInterface;

    @BeforeEach
    void init(){
        String AvroTree = "SimpleJoin.json";
        CacheTypeEnum cacheTypeEnum = CacheTypeEnum.FILE;
        treeQuerySetting = TreeQuerySettingHelper.createFromYaml();
        jsonString = TestDataAgent.prepareNodeFromJsonInstruction(AvroTree);
        avroSchemaHelper = new BasicAvroSchemaHelperImpl();
        discoveryServiceInterface = mock(DiscoveryServiceInterface.class);
        CacheInputInterfaceProxyFactory cacheInputInterfaceProxyFactory = new LocalCacheInputInterfaceProxyFactory();

        cacheInputInterface = mock(CacheInputInterface.class);

        treeQueryClusterRunnerProxyInterface = LocalDummyTreeQueryClusterRunnerProxy.builder()
                .treeQuerySetting(treeQuerySetting)
                .avroSchemaHelper(avroSchemaHelper)
                .createLocalTreeQueryClusterRunnerFunc(
                        (_Cluster)-> {
                            TreeQuerySetting remoteDummyTreeQuerySetting = new TreeQuerySetting.TreeQuerySettingBuilder(
                                    _Cluster.getClusterName(),
                                    treeQuerySetting.getServicehostname(),
                                    treeQuerySetting.getServicePort(),
                                    treeQuerySetting.getCacheFilePath(),
                                    treeQuerySetting.getRedisHostName(),
                                    treeQuerySetting.getRedisPort(),
                                    treeQuerySetting.getServiceDiscoveryHostName(),
                                    treeQuerySetting.getServiceDiscoveryPort()
                            ).build();

                            return  LocalTreeQueryClusterRunner.builder()
                                    .avroSchemaHelper(avroSchemaHelper)
                                    .beamCacheOutputBuilder(BeamCacheOutputBuilder.builder()
                                            .treeQuerySetting(treeQuerySetting)
                                            .build())
                                    .discoveryServiceInterface(discoveryServiceInterface)
                                    .treeQuerySetting(remoteDummyTreeQuerySetting)
                                    .cacheInputInterface(cacheInputInterface)
                                    .build();
                        }
                )
                .build();
        batchTreeQueryBeamServiceHelper = BatchTreeQueryBeamServiceHelper.builder()
                .avroSchemaHelper(avroSchemaHelper)
                .discoveryServiceInterface(discoveryServiceInterface)
                .treeQuerySetting(treeQuerySetting)
                .treeQueryClusterRunnerProxyInterface(treeQueryClusterRunnerProxyInterface)
                .cacheInputInterface(cacheInputInterface)
                .build();
    }

    @Test
    void throwIllegalArugmentExceptionIfBlankProxy(){
        String AvroTree = "SimpleJoinCluster.json";
        jsonString = TestDataAgent.prepareNodeFromJsonInstruction(AvroTree);
        batchTreeQueryBeamServiceHelper = BatchTreeQueryBeamServiceHelper.builder()
                .avroSchemaHelper(avroSchemaHelper)
                .discoveryServiceInterface(discoveryServiceInterface)
                .cacheInputInterface(cacheInputInterface)
                .treeQuerySetting(treeQuerySetting)
                .build();
        int pageSize = 3;
        DataConsumer2LinkedList genericRecordConsumer = new DataConsumer2LinkedList();
        PreprocessInput preprocessInput = batchTreeQueryBeamServiceHelper.preprocess(jsonString);

       // assertThrows(IllegalStateException.class,
                //()->{
        ReturnResult returnResult = batchTreeQueryBeamServiceHelper.runAndPageResult(TreeQueryRequest.RunMode.DIRECT,
                            preprocessInput,
                            true,
                            pageSize,
                            2,
                            genericRecordConsumer);
                //}
                //);
        log.debug(returnResult.getStatusTreeQueryCluster().toString());
        assertEquals(StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR,returnResult.getStatusTreeQueryCluster().getStatus());

    }



    @Test
    void happyPathRunBeamJoinLocally() {
        //TreeQueryRequest treeQueryRequest =  TreeQueryRequest.
        int pageSize = 3;
        DataConsumer2LinkedList genericRecordConsumer = new DataConsumer2LinkedList();
        PreprocessInput preprocessInput = batchTreeQueryBeamServiceHelper.preprocess(jsonString);

        ReturnResult returnResult = batchTreeQueryBeamServiceHelper.runAndPageResult(TreeQueryRequest.RunMode.DIRECT,
                preprocessInput,
                true,
                pageSize,
                2,
                genericRecordConsumer);

        assertThat(returnResult.getStatusTreeQueryCluster().getStatus()).isEqualTo(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS);
        assertThat(genericRecordConsumer.getGenericRecordList()).hasSize(pageSize);
        genericRecordConsumer.getGenericRecordList().forEach(
                genericRecord -> {
                    //log.debug(genericRecord.toString());
                    assertThat(genericRecord.toString()).isNotBlank();
                }
        );

    }

    @Test
    void CheckGetFromCacheRecord() {
        int pageSize = 100;
        DataConsumer2Set genericRecordConsumer = new DataConsumer2Set();
        PreprocessInput preprocessInput = batchTreeQueryBeamServiceHelper.preprocess(jsonString);

        ReturnResult returnResult = batchTreeQueryBeamServiceHelper.runAndPageResult(TreeQueryRequest.RunMode.DIRECT,
                preprocessInput,
                true,
                pageSize,
                2,
                genericRecordConsumer);

        assertThat(returnResult.getStatusTreeQueryCluster().getStatus()).isEqualTo(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS);
        assertThat(genericRecordConsumer.getGenericRecordSet()).hasSize(pageSize);
        genericRecordConsumer.getGenericRecordSet().forEach(
                genericRecord -> {
                    //log.debug(genericRecord.toString());
                    assertThat(genericRecord.toString()).isNotBlank();
                }
        );
        DataConsumer2Set cachedRecordConsumer = new DataConsumer2Set();
        ReturnResult returnResult2 = batchTreeQueryBeamServiceHelper.runAndPageResult(TreeQueryRequest.RunMode.DIRECT,
                preprocessInput,
                false,
                pageSize,
                2,
                cachedRecordConsumer);
        assertThat(returnResult2.getStatusTreeQueryCluster().getStatus()).isEqualTo(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS);
        assertThat(cachedRecordConsumer.getGenericRecordSet()).hasSize(pageSize);
        assertThat(returnResult2.getStatusTreeQueryCluster().getDescription()).isEqualTo("Fresh from cache");
        genericRecordConsumer.getGenericRecordSet().forEach(
                genericRecord -> {
                    assertThat(cachedRecordConsumer.getGenericRecordSet()).contains(genericRecord);
                }
        );
    }

    private static class DataConsumer2LinkedList implements Consumer<GenericRecord>{
        @Getter
        List<GenericRecord> genericRecordList = Lists.newLinkedList();
        @Override
        public void accept(GenericRecord genericRecord) {
            genericRecordList.add(genericRecord);
        }
    }
    private static class DataConsumer2Set implements Consumer<GenericRecord>{
        @Getter
        Set<GenericRecord> genericRecordSet = Sets.newHashSet();
        @Override
        public void accept(GenericRecord genericRecord) {
            genericRecordSet.add(genericRecord);
        }
    }
}