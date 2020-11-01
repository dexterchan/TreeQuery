package org.treequery.beam.transform;

import org.apache.avro.generic.GenericRecord;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.values.PCollection;
import org.treequery.model.Node;

import java.util.List;

public class DelayLoadLeafNodeHelper extends LoadLeafNodeHelper{
    @Override
    public PCollection<GenericRecord> apply(Pipeline pipeline, List<PCollection<GenericRecord>> parentCollectionLst, Node node) {
        PCollection<GenericRecord> genericRecordPCollection =  super.apply(pipeline, parentCollectionLst, node);
        try {
            /*
                This code reveal that there is synchronization issue in the async runner
                WE have to delay the load file to allow SQL, MOngo query to catchup
             */
            Thread.sleep((int)(Math.random() * 10000));
        }catch(Exception e){}
        return genericRecordPCollection;
    }
}
