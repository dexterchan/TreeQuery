package org.treequery.model;

import lombok.Builder;
import lombok.Getter;
import org.treequery.utils.AvroSchemaHelper;

import java.util.Optional;

//Reference Avro: https://avro.apache.org/docs/current/gettingstartedjava.html#Serializing
@Getter
public  class CacheNode extends Node implements DataSource{
    protected Node originalNode;
    CacheTypeEnum cacheTypeEnum;
    AvroSchemaHelper avroSchemaHelper;
    public CacheNode(){
        super();
    }

    @Builder
    CacheNode(Node node, CacheTypeEnum cacheTypeEnum, AvroSchemaHelper avroSchemaHelper){
        this();
        this.originalNode = node;
        this.cacheTypeEnum = cacheTypeEnum;
        this.avroSchemaHelper = Optional.ofNullable(avroSchemaHelper).orElseThrow(()->new IllegalArgumentException("Avro Schema Helper not null"));
        this.description = node.getDescription();
        this.cluster = node.getCluster();
        this.action = node.getAction();
        this.jNode = node.jNode;
    }

    @Override
    public int hashCode() {
        return originalNode.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return originalNode.equals(obj);
    }

    public String toString() {
        return String.format("Cache(%s)",this.originalNode.toString());
    }

    @Override
    public String getSource() {
        return this.originalNode.getIdentifier();
    }

    @Override
    public String getAvro_schema() {
        return avroSchemaHelper.getAvroSchema(this.originalNode).toString();
    }

    @Override
    public String getIdentifier(){
        return this.originalNode.getIdentifier();
    }
    @Override
    public String toJson(){
        return this.originalNode.toJson();
    }
    @Override
    public String getName(){
        return this.originalNode.getName();
    }
    @Override
    public String getDescription(){
        return this.originalNode.getDescription();
    }
}
