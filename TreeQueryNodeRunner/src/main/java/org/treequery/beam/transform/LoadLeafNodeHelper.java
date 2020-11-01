package org.treequery.beam.transform;

import org.treequery.Transform.LoadLeafNode;
import org.treequery.model.Node;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.values.PCollection;

import java.util.List;

public class LoadLeafNodeHelper implements NodeBeamHelper{
    @Override
    public PCollection<GenericRecord> apply(Pipeline pipeline, List<PCollection<GenericRecord>> parentCollectionLst, Node node) {
        if (!( node instanceof LoadLeafNode)){
            throw new IllegalArgumentException(String.format("%s is not Leaf Avro File Node", node.toString()));
        }
        if (parentCollectionLst.size() > 0){
            throw new IllegalArgumentException("Parent nodes should be empty for Leaf Avro File Node");
        }
        LoadLeafNode loadLeafNode = (LoadLeafNode) node;
        Schema schema = loadLeafNode.getAvroSchemaObj();
        PCollection<GenericRecord> avroDocuments = pipeline.apply(AvroIO.readGenericRecords(schema).from(loadLeafNode.getSource()));
        return avroDocuments;
    }
}
