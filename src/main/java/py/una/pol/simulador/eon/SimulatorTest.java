/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package py.una.pol.simulador.eon;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.jgrapht.Graph;
import py.una.pol.simulador.eon.models.AssignFsResponse;
import py.una.pol.simulador.eon.models.Demand;
import py.una.pol.simulador.eon.models.EstablishedRoute;
import py.una.pol.simulador.eon.models.Input;
import py.una.pol.simulador.eon.models.Link;
import py.una.pol.simulador.eon.models.enums.RSAEnum;
import py.una.pol.simulador.eon.models.enums.TopologiesEnum;
import py.una.pol.simulador.eon.rsa.Algorithms;
import py.una.pol.simulador.eon.utils.MathUtils;
import py.una.pol.simulador.eon.utils.Utils;

/**
 *
 * @author Néstor E. Reinoso Wood
 */
public class SimulatorTest {

    /**
     * Configuración inicial para el simulador
     *
     * @param erlang Erlang para la simulación
     * @return Datos de entrada del simulador
     */
    private Input getTestingInput(Integer erlang) {
        Input input = new Input();

        input.setDemands(500);
        input.setTopologies(new ArrayList<>());
        input.getTopologies().add(TopologiesEnum.NSFNET);
        // input.getTopologies().add(TopologiesEnum.USNET);
        // input.getTopologies().add(TopologiesEnum.JPNNET);
        input.setFsWidth(new BigDecimal("12.5"));
        input.setFsRangeMax(8);
        input.setFsRangeMin(2);
        input.setCapacity(320);
        input.setCores(7);
        input.setLambda(5);
        input.setErlang(erlang);
        input.setAlgorithms(new ArrayList<>());
        // input.getAlgorithms().add(RSAEnum.CORE_UNICO);
        input.getAlgorithms().add(RSAEnum.MULTIPLES_CORES);
        input.setSimulationTime(MathUtils.getSimulationTime(input.getDemands(), input.getLambda()));
        // input.setMaxCrosstalk(new BigDecimal("0.003162277660168379331998893544")); // XT = -25 dB
        input.setMaxCrosstalk(new BigDecimal("0.031622776601683793319988935444")); //
        // XT = -15 dB
        input.setCrosstalkPerUnitLenghtList(new ArrayList<>());
        // input.getCrosstalkPerUnitLenghtList().add((2 * Math.pow(0.0035, 2) * 0.080) / (4000000 * 0.000045));
        input.getCrosstalkPerUnitLenghtList().add((2 * Math.pow(0.00040, 2) * 0.050));
        // / (4000000 * 0.000040));
        // input.getCrosstalkPerUnitLenghtList().add((2 * Math.pow(0.0000316, 2) *
        // 0.055) / (4000000 * 0.000045));
        return input;
    }

    /**
     * Simulador
     *
     * @param args Argumentos de entrada (Vacío)
     */
    public static void main(String[] args) {
        try {
            createTable();
            // Datos de entrada
            for (int erlang = 2000; erlang <= 2000; erlang = erlang + 1000) {

                Input input = new SimulatorTest().getTestingInput(erlang);
                for (TopologiesEnum topology : input.getTopologies()) {

                    // Se genera la red de acuerdo a los datos de entrada
                    Graph<Integer, Link> graph = Utils.createTopology(topology,
                            input.getCores(), input.getFsWidth(), input.getCapacity());

                    // GraphUtils.createImage(graph, topology.label());
                    // Contador de demandas utilizado para identificación
                    Integer demandsQ = 1;
                    List<List<Demand>> listaDemandas = new ArrayList<>();
                    for (int i = 0; i < input.getSimulationTime(); i++) {
                        List<Demand> demands = Utils.generateDemands(input.getLambda(),
                                input.getSimulationTime(), input.getFsRangeMin(),
                                input.getFsRangeMax(), graph.vertexSet().size(),
                                input.getErlang() / input.getLambda(), demandsQ, i);

                        demandsQ += demands.size();
                        listaDemandas.add(demands);
                    }

                    for (Double crosstalkPerUnitLength : input.getCrosstalkPerUnitLenghtList()) {
                        for (RSAEnum algorithm : input.getAlgorithms()) {
                            graph = Utils.createTopology(topology,
                                    input.getCores(), input.getFsWidth(), input.getCapacity());
                            // Lista de rutas establecidas durante la simulación
                            List<EstablishedRoute> establishedRoutes = new ArrayList<>();
                            System.out.println("Inicializando simulación del RSA " + algorithm.label()
                                    + " para erlang: " + (erlang) + " para la topología " + topology.label() + " y H = "
                                    + crosstalkPerUnitLength.toString());
                            int demandaNumero = 1;
                            int bloqueos = 0;
                            // Iteración de unidades de tiempo
                            for (int i = 0; i < input.getSimulationTime(); i++) {
                                System.out.println("Tiempo: " + (i + 1));
                                // Generación de demandas para la unidad de tiempo
                                List<Demand> demands = listaDemandas.get(i);
                                // System.out.println("Demandas a insertar: " + demands.size());
                                for (Demand demand : demands) {
                                    demandaNumero++;
                                    // System.out.println("Insertando demanda " + demandaNumero++);
                                    // k caminos más cortos entre source y destination de la demanda actual

                                    EstablishedRoute establishedRoute;
                                    switch (algorithm) {
                                        case CORE_UNICO -> {
                                            establishedRoute = Algorithms.ruteoCoreUnico(graph, demand,
                                                    input.getCapacity(), input.getCores(), input.getMaxCrosstalk(),
                                                    crosstalkPerUnitLength);
                                        }
                                        case MULTIPLES_CORES -> {
                                            establishedRoute = Algorithms.ruteoCoreMultiple(graph, demand,
                                                    input.getCapacity(), input.getCores(), input.getMaxCrosstalk(),
                                                    crosstalkPerUnitLength);
                                        }
                                        default -> {
                                            establishedRoute = null;
                                        }
                                    }

                                    if (establishedRoute == null || establishedRoute.getFsIndexBegin() == -1) {
                                        // Bloqueo
                                        // System.out.println("BLOQUEO");
                                        demand.setBlocked(true);
                                        insertData(algorithm.label(), topology.label(), "" + i, "" + demand.getId(),
                                                "" + erlang, crosstalkPerUnitLength.toString());
                                        bloqueos++;
                                    } else {
                                        // Ruta establecida
                                        // System.out.println("Cores: " + establishedRoute.getPathCores());
                                        AssignFsResponse response = Utils.assignFs(graph, establishedRoute,
                                                crosstalkPerUnitLength);
                                        establishedRoute = response.getRoute();
                                        graph = response.getGraph();
                                        establishedRoutes.add(establishedRoute);
                                    }

                                }

                                for (EstablishedRoute route : establishedRoutes) {
                                    route.subLifeTime();
                                }

                                for (int ri = 0; ri < establishedRoutes.size(); ri++) {
                                    EstablishedRoute route = establishedRoutes.get(ri);
                                    if (route.getLifetime().equals(0)) {
                                        // Antes de desasignar, obtener información de la ruta
                                        List<Integer> affectedCores = route.getPathCores();
                                        List<Link> affectedLinks = route.getPath();

                                        // Desasignar la ruta actual
                                        Utils.deallocateFs(graph, route, crosstalkPerUnitLength);

                                        // Desfragmentar los enlaces afectados
                                        System.out.println("Desfragmentando enlaces afectados" + affectedLinks);
                                        System.out.println("Desfragmentando cores afectados" + affectedCores);
                                        // for (int r1 = 0; r1 < affectedLinks.size(); r1++) {
                                        // Link link = affectedLinks.get(r1);
                                        // Integer core = affectedCores.get(r1);
                                        // desfragmentarEnlace(graph, link, core, establishedRoutes,
                                        // crosstalkPerUnitLength);

                                        // }

                                        establishedRoutes.remove(ri);
                                        ri--;
                                    }
                                }
                            }
                            System.out.println("TOTAL DE BLOQUEOS: " + bloqueos);
                            System.out.println("Cantidad de demandas: " + demandaNumero);
                            System.out.println(System.lineSeparator());
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void desfragmentarEnlace(
            Graph<Integer, Link> graph,
            Link link,
            Integer core,
            List<EstablishedRoute> establishedRoutes,
            Double crosstalkPerUnitLength) {

        List<EstablishedRoute> rutasAfectadas = new ArrayList<>();

        // 1. Identificar todas las rutas que pasan por este enlace y núcleo
        for (EstablishedRoute route : establishedRoutes) {
            if (route.getPath().contains(link) &&
                    route.getPathCores().get(route.getPath().indexOf(link)).equals(core)) {
                rutasAfectadas.add(route);
            }
        }

        // 2. Ordenar las rutas por su índice de inicio de FS
        rutasAfectadas.sort((r1, r2) -> r1.getFsIndexBegin().compareTo(r2.getFsIndexBegin()));

        // 3. Reasignar FS comenzando desde el índice más bajo posible
        int currentFsIndex = 0;
        for (EstablishedRoute route : rutasAfectadas) {
            // Usar fsWidth en lugar de getDemand().getFs()
            int fsCount = route.getFsWidth();

            // Si la posición actual es diferente a la original
            if (currentFsIndex != route.getFsIndexBegin()) {
                // Desasignar la ruta actual
                Utils.deallocateFs(graph, route, crosstalkPerUnitLength);

                // Actualizar el índice de inicio
                route.setFsIndexBegin(currentFsIndex);

                // Reasignar la ruta en la nueva posición
                AssignFsResponse response = Utils.assignFs(graph, route, crosstalkPerUnitLength);
                if (response != null) {
                    graph = response.getGraph();
                }
            }

            currentFsIndex += fsCount;
        }
    }

    /**
     * Inserta los datos en la BD
     *
     * @param rsa       Algoritmo RSA utilizado
     * @param topologia Topología de la red
     * @param tiempo    Tiempo del bloqueo
     * @param demanda   Demanda bloqueada
     * @param erlang    Erlang de la simulación
     * @param h         Crosstalk por unidad de longitud de la simulación
     */
    public static void insertData(String rsa, String topologia, String tiempo, String demanda, String erlang,
            String h) {
        Connection c;

        Statement stmt;

        try {

            Class.forName("org.sqlite.JDBC");

            c = DriverManager.getConnection("jdbc:sqlite:simulador.db");

            c.setAutoCommit(false);

            stmt = c.createStatement();
            String sql = "INSERT INTO Bloqueos (rsa, topologia, tiempo, demanda, erlang, h) "
                    + "VALUES ('" + rsa + "','" + topologia + "', '" + tiempo + "' ,'" + demanda + "', " + "'" + erlang
                    + "', " + "'" + h + "')";
            stmt.executeUpdate(sql);
            stmt.close();
            c.commit();
            c.close();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
    }

    /**
     * Generación de la tabla de resultados
     */
    public static void createTable() {
        Connection c;

        Statement stmt;

        try {

            Class.forName("org.sqlite.JDBC");

            c = DriverManager.getConnection("jdbc:sqlite:simulador.db");

            System.out.println("Database Opened...\n");

            stmt = c.createStatement();

            String dropTable = "DROP TABLE Bloqueos ";

            String sql = "CREATE TABLE IF NOT EXISTS Bloqueos "
                    + "("
                    + "erlang TEXT NOT NULL, "
                    + "rsa TEXT NOT NULL, "
                    + " topologia TEXT NOT NULL, "
                    + " h TEXT NOT NULL, "
                    + " tiempo TEXT NOT NULL, "
                    + " demanda TEXT NOT NULL) ";
            try {
                stmt.executeUpdate(dropTable);
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
            stmt.executeUpdate(sql);
            stmt.close();
            c.close();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
    }
}
