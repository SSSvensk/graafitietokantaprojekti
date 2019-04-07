package graph;

public class Edge {

    public Edge(boolean directed, String primaryKeyTableName, String primaryKeyColumnValue, String foreignKeyTableName, String foreignKeyTableValue, String graphName) {

        this.directed = directed;
        this.primaryKeyTableName = primaryKeyTableName;
        this.primaryKeyColumnValue = primaryKeyColumnValue;
        this.foreignKeyTableName = foreignKeyTableName;
        this.foreignKeyTableValue = foreignKeyTableValue;
        this.graphName = graphName;

    }


    private boolean directed;
    private String graphName;
    private String primaryKeyTableName;
    private String primaryKeyColumnValue;
    private String foreignKeyTableName;
    private String foreignKeyTableValue;


    public void print() {

        //System.out.println("  Edge " + edgeId + " from node " + table1Id + " to node " + table2Id + ".");

    }

}
