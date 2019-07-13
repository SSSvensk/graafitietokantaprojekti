import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TinkerPopGraph {

    public TinkerPopGraph(int graphId) {
        this.graphId = graphId;
    }

    private int graphId;

    private LinkedList<String> primaryKeysOfTableValues;

    private HashMap<String, LinkedList<String>> primaryKeysOfTables;
    private HashMap<String, LinkedList<String>> allThePrimaryKeysOfTables;
    private HashMap<String, LinkedList<String>> allTheRealPrimaryKeyValuesOfTables;

    public HashMap<HashMap<String, LinkedList<String>>, Vertex> getVertexes() {
        return vertexes;
    }

    public void setVertexes(HashMap<HashMap<String, LinkedList<String>>, Vertex> vertexes) {
        this.vertexes = vertexes;
    }

    private HashMap<HashMap<String, LinkedList<String>>, Vertex> vertexes;

    private Graph graph;

    private GraphTraversalSource graphTraversalSource;

    static final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    static final String DB_URL = "jdbc:mariadb://127.0.0.1/";

    //  Database credentials
    static final String USERNAME = "root";
    static final String PASSWORD = "root";

    private Cluster cluster = null;
    private Client client = null;

    public TinkerPopGraph() {
        this.vertexes = new HashMap<HashMap<String, LinkedList<String>>, Vertex>();
        this.primaryKeysOfTables = new HashMap<String, LinkedList<String>>();
        this.allThePrimaryKeysOfTables = new HashMap<String, LinkedList<String>>();
        this.allTheRealPrimaryKeyValuesOfTables = new HashMap<String, LinkedList<String>>();
        this.graphTraversalSource = traversal().withRemote(DriverRemoteConnection.using("localhost",8182,"g"));
    }


    public ResultSet executeSQLQuery(String sqlQuery) {

        Connection conn = null;
        Statement stmt = null;
        ResultSet resultSet = null;

        try {

            Class.forName(JDBC_DRIVER);

            conn = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);
            stmt = conn.createStatement();

            resultSet = stmt.executeQuery(sqlQuery);

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            try {
                if (stmt != null) {
                    conn.close();
                }
            } catch (SQLException se) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }

    return resultSet;
    }

    public void getEdges(String schema) {

        System.out.println("Loading edges");

        Connection conn = null;
        Statement stmt = null;
        ResultSet resultSetTablesList = null;

        try {

            Class.forName(JDBC_DRIVER );
            conn = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);
            DatabaseMetaData dbMetaData = conn.getMetaData();
            String[] types = {"TABLE"};
            resultSetTablesList = dbMetaData.getTables(schema, null, "%", types);

            this.cluster = Cluster.build().addContactPoint("localhost").reconnectInterval(500).create();
            this.client = cluster.connect();

            //Tutkitaan skeemassa olevien taulujen joukkoa

            while (resultSetTablesList.next()) {

                String tableName = resultSetTablesList.getString(3);

                LinkedList<String> realPrimaryKeysOfTableValues = new LinkedList<String>();

                //Haetaan käsiteltävän taulun pääavaimet

                ResultSet resultSetPrimaryKeysList = dbMetaData.getPrimaryKeys(schema, schema, tableName);
                LinkedList<String> primaryKeysOfTable = new LinkedList<String>();

                while (resultSetPrimaryKeysList.next()) {

                    primaryKeysOfTable.add(resultSetPrimaryKeysList.getString("COLUMN_NAME"));

                }

                ResultSet foreignKeys = dbMetaData.getImportedKeys(schema, schema, tableName);
                HashMap<Integer, HashMap<String, String>> foreignKeysOfTable = new HashMap<Integer, HashMap<String, String>>();

                boolean foreignKeysArePrimaryKeys = true;

                int foreignKeysCounter = 0;
                while (foreignKeys.next()) {

                    String fkTableName = foreignKeys.getString("FKTABLE_NAME");
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");
                    String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");

                    HashMap<String, String> foreignKeysInformation = new HashMap<String, String>();

                    foreignKeysInformation.put("fkTableName", fkTableName);
                    foreignKeysInformation.put("fkColumnName", fkColumnName);
                    foreignKeysInformation.put("pkTableName", pkTableName);
                    foreignKeysInformation.put("pkColumnName", pkColumnName);

                    foreignKeysOfTable.put(foreignKeysCounter, foreignKeysInformation);
                    foreignKeysCounter++;

                    if(!primaryKeysOfTable.contains(fkColumnName)) {
                        foreignKeysArePrimaryKeys = false;
                    }

                }

                LinkedList<String> foreignKeysOfTableValues = new LinkedList<String>();

                if(!foreignKeysArePrimaryKeys) {

                    //Haetaan taulun vierasavaimet ja käydään ne läpi

                    for (int foreignKeyIndex : foreignKeysOfTable.keySet()) {

                        HashMap<String, String> foreignKeyInformation = foreignKeysOfTable.get(foreignKeyIndex);

                        String fkColumnName = foreignKeyInformation.get("fkColumnName");
                        String foreignTableName = foreignKeyInformation.get("pkTableName");
                        String pkColumnName = foreignKeyInformation.get("pkColumnName");

                        //Haetaan taulun pääavain-vierasavain -parit tietokannasta ja käydään ne läpi

                        String primaryKeys = String.join(",", primaryKeysOfTable);

                        ResultSet primaryKeyForeignKeyValues = executeSQLQuery("SELECT " + primaryKeys + "," + fkColumnName + " FROM " + schema + "." + tableName + " ORDER BY " + primaryKeys);

                        LinkedList<Edge> edges = new LinkedList<Edge>();

                        while (primaryKeyForeignKeyValues.next()) {

                            //Haetaan vierasavaimen viitaaman taulun pääavaimet

                            ResultSet foreignKeysOfForeignTable = dbMetaData.getImportedKeys(schema, schema, foreignTableName);
                            LinkedList<String> foreignKeysOfForeignTableList = new LinkedList<String>();

                            while (foreignKeysOfForeignTable.next()) {
                                foreignKeysOfForeignTableList.add(foreignKeysOfForeignTable.getString("FKCOLUMN_NAME"));
                            }

                            //Jos viitatun taulun pääavaimen koko on 1, lisätään kaari

                            if (foreignKeysOfForeignTableList.size() < 2) {

                                //Kaareen lisätään käsitelävän taulun nimi, sen pääavain, vierastaulun nimi ja sen pääavain.

                                LinkedList<String> primaryKeysOfTableValues = new LinkedList<String>();
                                for (int i=0; i<primaryKeysOfTable.size(); i++) {
                                    primaryKeysOfTableValues.add(primaryKeyForeignKeyValues.getString(primaryKeysOfTable.get(i)));
                                }

                                realPrimaryKeysOfTableValues.addAll(primaryKeysOfTableValues);
                                foreignKeysOfTableValues.add(primaryKeyForeignKeyValues.getString(fkColumnName));

                                String primaryKeysOfTableValuesAsString = primaryKeysOfTableValues.toString().replace("[", "").replace("]", "");
                                String foreignKeysOfTableValuesAsString = foreignKeysOfTableValues.toString().replace("[", "").replace("]", "");

                                String script = "v1=g.V().has('tableName', '" + tableName  + "').has('primaryKeysOfTableValues', '" + primaryKeysOfTableValuesAsString  + "');\n" +
                                                "v2=g.V().has('tableName', '" + foreignTableName  + "').has('primaryKeysOfTableValues', '" + foreignKeysOfTableValuesAsString  + "');\n" +
                                                 "g.addE(\"1toN\").from(v1).to(v2)";

                                /*
                                final String script = "g.V().drop().iterate();\n" +
                                        "\n" +
                                        "List ids = new ArrayList();\n" +
                                        "\n" +
                                        "int ii = 0;\n" +
                                        "Vertex v = graph.addVertex();\n" +
                                        "v.property(\"ii\", ii);\n" +
                                        "v.property(\"sin\", Math.sin(ii));\n" +
                                        "ids.add(v.id());\n" +
                                        "\n" +
                                        "Random rand = new Random();\n" +
                                        "for (; ii < size; ii++) {\n" +
                                        "    v = graph.addVertex();\n" +
                                        "    v.property(\"ii\", ii);\n" +
                                        "    v.property(\"sin\", Math.sin(ii/5.0));\n" +
                                        "    Vertex u = g.V(ids.get(rand.nextInt(ids.size()))).next();\n" +
                                        "    v.addEdge(\"linked\", u);\n" +
                                        "    ids.add(u.id());\n" +
                                        "    ids.add(v.id());\n" +
                                        "}\n" +
                                        "g.V()";

                                */

                                client.submit(script);


                                /*
                                Vertex firstVertex = null;

                                for (HashMap<String, LinkedList<String>> vertexKeys : vertexes.keySet()) {

                                    String key = (String) vertexKeys.keySet().toArray()[0];
                                    LinkedList<String> listOfKeys = vertexKeys.get(key);

                                    if(key.equals(tableName) && listOfKeys.equals(primaryKeysOfTableValues)) {

                                        Vertex vertex1 = vertexes.get(vertexKeys);

                                        for (HashMap<String, LinkedList<String>> vertexKeys2 : vertexes.keySet()) {

                                            String key2 = (String) vertexKeys2.keySet().toArray()[0];
                                            LinkedList<String> listOfKeys2 = vertexKeys2.get(key2);

                                            if (key2.equals(foreignTableName) && listOfKeys2.equals(foreignKeysOfTableValues)) {

                                                Vertex vertex2 = vertexes.get(vertexKeys2);
                                                vertex1.addEdge("1toN",vertex2);

                                                client.submit("v=graph.addVertex('tableName','" + tableName  + "','primaryKeysOfTableValues'," + primaryKeysOfTableValuesAsString + ",'jsonArray','" + jsonArray + "');");

:> g.V().has("name", "neptune")

                                                break;
                                            }

                                        }

                                    }

                                }*/

                            }

                        }

                    }

                } else {

                    //Haetaan taulun vierasavaimet ja käydään ne läpi

                    HashMap<String, String> referencedTables = new HashMap<String, String>();

                    String foreignTableName = null;

                    for (int foreignKeyIndex : foreignKeysOfTable.keySet()) {

                        HashMap<String, String> foreignKeyInformation = foreignKeysOfTable.get(foreignKeyIndex);

                        String fkColumnName = foreignKeyInformation.get("fkColumnName");
                        foreignTableName = foreignKeyInformation.get("pkTableName");

                        referencedTables.put(fkColumnName, foreignTableName);

                    }


                    if(referencedTables.size() == 2) {

                        Object[] keySet = referencedTables.keySet().toArray();
                        ResultSet relationTableValues = executeSQLQuery("SELECT * FROM " + schema + "." + tableName);

                        LinkedList<Edge> edges = new LinkedList<Edge>();

                        while (relationTableValues.next()) {

                            String table1Value = relationTableValues.getString(keySet[0].toString());
                            String table2Value = relationTableValues.getString(keySet[1].toString());

                            LinkedList<String> table1Values = new LinkedList<String>();
                            table1Values.add(table1Value);

                            LinkedList<String> table2Values = new LinkedList<String>();
                            table2Values.add(table2Value);

                            int total_rows = relationTableValues.getMetaData().getColumnCount();

                            JSONArray jsonArray = new JSONArray();

                            for (int i = 0; i < total_rows; i++) {
                                JSONObject obj = new JSONObject();
                                obj.put(relationTableValues.getMetaData().getColumnLabel(i + 1)
                                        .toLowerCase(), relationTableValues.getObject(i + 1));
                                jsonArray.put(obj);
                            }

                            String primaryKeysOfTableValuesAsString = primaryKeysOfTableValues.toString().replace("[", "").replace("]", "");
                            String foreignKeysOfTableValuesAsString = foreignKeysOfTableValues.toString().replace("[", "").replace("]", "");

                            String script = "v1=g.V().has('tableName', '" + tableName  + "').has('primaryKeysOfTableValues', '" + primaryKeysOfTableValuesAsString  + "');\n" +
                                    "v2=g.V().has('tableName', '" + foreignTableName  + "').has('primaryKeysOfTableValues', '" + foreignKeysOfTableValuesAsString  + "');\n" +
                                    "g.addE('" + tableName + "').from(v1).to(v2).property('jsonArray','" + jsonArray + "')";

                            client.submit(script);
                            /*

                            Vertex firstVertex = null;

                            for (HashMap<String, LinkedList<String>> vertexKeys : vertexes.keySet()) {

                                String key = (String) vertexKeys.keySet().toArray()[0];
                                LinkedList<String> listOfKeys = vertexKeys.get(key);

                                if(key.equals(tableName) && listOfKeys.equals(table1Values)) {

                                    Vertex vertex1 = vertexes.get(vertexKeys);

                                    for (HashMap<String, LinkedList<String>> vertexKeys2 : vertexes.keySet()) {

                                        String key2 = (String) vertexKeys2.keySet().toArray()[0];
                                        LinkedList<String> listOfKeys2 = vertexKeys2.get(key2);

                                        if (key2.equals(foreignTableName) && listOfKeys2.equals(table2Values)) {

                                            Vertex vertex2 = vertexes.get(vertexKeys2);
                                            vertex1.addEdge(tableName, vertex2, "jsonArray",jsonArray).bothVertices();
                                            break;

                                        }

                                    }

                                }

                            }
                            */
                        }

                    }

                }

                allTheRealPrimaryKeyValuesOfTables.put(tableName,realPrimaryKeysOfTableValues);

            }

            conn.close();

            cluster.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            try {
                if (stmt != null) {
                    conn.close();
                }
            } catch (SQLException se) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }
    }

    public void printKeys() {

        for (String table: primaryKeysOfTables.keySet()){

            LinkedList<String> keys = primaryKeysOfTables.get(table);

            System.out.println("Keys for the table: " + table);

            for (String key : keys) {
                System.out.println(key);
            }

        }

    }

    public void printPrimaryKeysValues() {

        for (String table: allThePrimaryKeysOfTables.keySet()){

            LinkedList<String> keys = allThePrimaryKeysOfTables.get(table);

            System.out.println("Keys for the table: " + table);

            for (String key : keys) {
                System.out.println(key);
            }

        }

    }

    public void printVertexKeys() {

        for (HashMap<String, LinkedList<String>> vertexKeys : vertexes.keySet()) {

            String key = (String) vertexKeys.keySet().toArray()[0];
            LinkedList<String> listOfKeys = vertexKeys.get(key);

            System.out.println("Table: " + key + ", " + listOfKeys.toString());

            //System.out.println("    list of keys: ");
            //System.out.println("    " + listOfKeys.toString());

        }
    }

    public void printVertexes() {

        for (HashMap<String, LinkedList<String>> vertexKeys : vertexes.keySet()) {

            Vertex vertex = vertexes.get(vertexKeys);

            VertexProperty<String> tableNameInVertex = vertex.property("tableName");
            VertexProperty<LinkedList<String>> primaryKeysOfTableValuesInVertex = vertex.property("primaryKeysOfTableValues");
            VertexProperty<JSONArray> jsonArrayInVertex = vertex.property("jsonArray");

            System.out.println("Table: " + tableNameInVertex.value().toString() + ", keys: " + primaryKeysOfTableValuesInVertex.value().toString() + ", jsonArray: " + jsonArrayInVertex.value().toString());

        }

    }


    public void printRealPrimaryKeysValues() {

        for (String table: allTheRealPrimaryKeyValuesOfTables.keySet()){

            LinkedList<String> keys = allTheRealPrimaryKeyValuesOfTables.get(table);

            System.out.println("Real Keys for the table: " + table);

            for (String key : keys) {
                System.out.println(key);
            }

        }

    }

    /*

    VertexProperty<String> tableNameInVertex = vertex.property("tableName");

    VertexProperty<LinkedList<String>> primaryKeysOfTableValuesInVertex = vertex.property("primaryKeysOfTableValues");

    LinkedList<String> primaryKeysOfTableValues = primaryKeysOfTableValuesInVertex.value();

     */

    public void getVertexes(String schema) {

        System.out.println("Loading nodes");

        Connection conn = null;
        Statement stmt = null;
        ResultSet resultSetTablesList = null;

        try {

            Class.forName(JDBC_DRIVER );
            conn = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);
            DatabaseMetaData dbMetaData = conn.getMetaData();
            String[] types = {"TABLE"};
            resultSetTablesList = dbMetaData.getTables(schema, null, "%", types);

            this.cluster = Cluster.build().addContactPoint("localhost").reconnectInterval(500).create();
            this.client = cluster.connect();

            while (resultSetTablesList.next()) {

                String tableName = resultSetTablesList.getString(3);

                LinkedList<String> allThePrimaryKeysOfTableValues = new LinkedList<String>();

                ResultSet resultSetPrimaryKeysList = dbMetaData.getPrimaryKeys(schema, schema, tableName);
                LinkedList<String> primaryKeysOfTable = new LinkedList<String>();

                while (resultSetPrimaryKeysList.next()) {
                    primaryKeysOfTable.add(resultSetPrimaryKeysList.getString("COLUMN_NAME"));
                }

                ResultSet foreignKeysOfForeignTable = dbMetaData.getImportedKeys(schema, schema, tableName);
                LinkedList<String> foreignKeysOfForeignTableList = new LinkedList<String>();

                while (foreignKeysOfForeignTable.next()) {

                    foreignKeysOfForeignTableList.add(foreignKeysOfForeignTable.getString("FKCOLUMN_NAME"));

                }

                boolean foreignKeysArePrimaryKeys = true;

                if(foreignKeysOfForeignTableList.size() > 0) {

                    for (String fkColumnName : foreignKeysOfForeignTableList) {

                        if (!primaryKeysOfTable.contains(fkColumnName)) {
                            foreignKeysArePrimaryKeys = false;
                        }

                    }

                } else {

                    foreignKeysArePrimaryKeys = false;

                }

                if(!foreignKeysArePrimaryKeys) {

                    String primaryKeys = String.join(",", primaryKeysOfTable);

                    ResultSet resultSet = executeSQLQuery("SELECT * FROM " + schema + "." + tableName + " ORDER BY " + primaryKeys);

                    while (resultSet.next()) {

                        int total_rows = resultSet.getMetaData().getColumnCount();

                        JSONArray jsonArray = new JSONArray();

                        for (int i = 0; i < total_rows; i++) {
                            JSONObject obj = new JSONObject();
                            obj.put(resultSet.getMetaData().getColumnLabel(i + 1)
                                    .toLowerCase(), resultSet.getObject(i + 1));
                            jsonArray.put(obj);
                        }

                        LinkedList<String> primaryKeysOfTableValues = new LinkedList<String>();

                        for (String primaryKeyColumn : primaryKeysOfTable) {
                            primaryKeysOfTableValues.add(resultSet.getString(primaryKeyColumn));
                        }

                        allThePrimaryKeysOfTableValues.addAll(primaryKeysOfTableValues);

                        HashMap<HashMap<String, LinkedList<String>>, LinkedList<String>> vertexes;
                        HashMap<String, LinkedList<String>> keyMap = new HashMap<String, LinkedList<String>>();

                        keyMap.put(tableName, primaryKeysOfTableValues);

                        String jsonArrayAsString = jsonArray.toString().replace("\"", "");

                        String primaryKeysOfTableValuesAsString = primaryKeysOfTableValues.toString().replace("[", "").replace("]", "");

                        String gremlinScript = "v=graph.addVertex(\"name\",\"stephen\")";

                        String gremlinScript2 = "v=graph.addVertex(\"name\",\"" + tableName + primaryKeysOfTableValuesAsString + "\")";

                        //client.submit("v=graph.addVertex('name','" + tableName + primaryKeysOfTableValuesAsString + " ');").all().get().get(0).getVertex();

                        client.submit("v=graph.addVertex('tableName','" + tableName  + "','primaryKeysOfTableValues'," + primaryKeysOfTableValuesAsString + ",'jsonArray','" + jsonArray + "');");

                        /*
                        Vertex addedVertex = client.submit(gremlinScript).all().get().get(0).getVertex();

                        VertexProperty<String> tableNameInVertex = addedVertex.property("tableName");
                        VertexProperty<LinkedList<String>> primaryKeysOfTableValuesInVertex = addedVertex.property("primaryKeysOfTableValues");
                        VertexProperty<JSONArray> jsonArrayInVertex = addedVertex.property("jsonArray");

                        System.out.println("Table: " + tableNameInVertex.value().toString() + ", keys: " + primaryKeysOfTableValuesInVertex.value().toString() + ", jsonArray: " + jsonArrayInVertex.value().toString());

                        this.vertexes.put(keyMap,addedVertex);
                        */
                    }

                }

                allThePrimaryKeysOfTables.put(tableName,allThePrimaryKeysOfTableValues);

            }

            cluster.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            try {
                if (stmt != null) {
                    conn.close();
                }
            } catch (SQLException se) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException se) {
                se.printStackTrace();
            }

        }

    }

}
