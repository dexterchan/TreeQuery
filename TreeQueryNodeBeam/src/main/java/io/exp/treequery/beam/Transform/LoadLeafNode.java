package io.exp.treequery.beam.Transform;

import io.exp.treequery.model.DataSource;
import io.exp.treequery.model.Node;
import lombok.Getter;

@Getter
public class LoadLeafNode extends Node implements DataSource {
    String source;
    String avro_schema;

    @Override
    public String execute() {
        return null;
    }

    @Override
    public void undo(String id) {

    }
}
