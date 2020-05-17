package org.treequery.grpc.client;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import org.apache.avro.generic.GenericRecord;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.treequery.cluster.Cluster;
import org.treequery.discoveryservice.DiscoveryServiceInterface;
import org.treequery.discoveryservice.Exception.InterfaceMethodNotUsedException;
import org.treequery.discoveryservice.client.DynamoClient;
import org.treequery.discoveryservice.proxy.DiscoveryServiceProxyImpl;
import org.treequery.discoveryservice.proxy.LocalDummyDiscoveryServiceProxy;
import org.treequery.grpc.model.TreeQueryResult;
import org.treequery.grpc.utils.TestDataAgent;
import org.treequery.proto.TreeQueryRequest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class TreeQueryClientTest {

    static DiscoveryServiceInterface discoveryServiceInterface;

    @BeforeEach
    void init() {
//        DynamoDB dynamoDB = new DynamoClient("https://dynamodb.us-west-2.amazonaws.com").getDynamoDB();
//        discoveryServiceInterface = new DiscoveryServiceProxyImpl(dynamoDB);
        discoveryServiceInterface = new LocalDummyDiscoveryServiceProxy();
        try {
            discoveryServiceInterface.registerCluster(Cluster.builder()
                    .clusterName("A").build(), "localhost", 9002);
        } catch (InterfaceMethodNotUsedException ex) {
            System.err.println(ex.getMessage());
        }
    }

    @Test
    void happyPathSimpleJoin() {
        String AvroTree = "SimpleJoin.json";
        run(AvroTree, discoveryServiceInterface);
    }

    private void run(String AvroTree, DiscoveryServiceInterface discoveryServiceInterface) {
        String jsonString = TestDataAgent.prepareNodeFromJsonInstruction(AvroTree);
        TreeQueryClient treeQueryClient = TreeQueryClientFactory.
                createTreeQueryClientFromJsonInput(
                        jsonString,
                        discoveryServiceInterface);

        boolean renewCache = false;
        int pageSize = 100;
        int page = 1;
        TreeQueryResult treeQueryResult = null;
        AtomicLong counter = new AtomicLong(0);
        Set<GenericRecord> genericRecordSet = Sets.newHashSet();
        do {
            treeQueryResult = treeQueryClient.query(TreeQueryRequest.RunMode.DIRECT,
                    jsonString,
                    renewCache,
                    pageSize,
                    page
            );
            assertTrue(treeQueryResult.getHeader().isSuccess());
            assertEquals(0, treeQueryResult.getHeader().getErr_code());
            TreeQueryResult.TreeQueryResponseResult treeQueryResponseResult = treeQueryResult.getResult();

            List<GenericRecord> genericRecordList = treeQueryResponseResult.getGenericRecordList();
            genericRecordList.forEach(
                    genericRecord -> {
                        assertThat(genericRecord).isNotNull();
                        assertThat(genericRecord.get("bondtrade")).isNotNull();
                        assertThat(genericRecordSet).doesNotContain(genericRecord);
                        counter.incrementAndGet();
                        genericRecordSet.add(genericRecord);
                    }
            );
            page++;
        } while (treeQueryResult != null && treeQueryResult.getResult().getDatasize() != 0);
        assertEquals(1000, counter.get());
        assertThat(genericRecordSet).hasSize(1000);
    }
}