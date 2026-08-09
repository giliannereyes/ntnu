import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;

// GUI
import javax.swing.*; // Windows
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

// JMapViewer
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.JMapViewerEventListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.BingAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;
import org.openstreetmap.gui.jmapviewer.*;

/*
  Road navigation program with GUI for entering place names and displaying routes.

  Features:
  * Reading graph from file (nodes, edges, points of interest)
  * Dijkstra's algorithm for shortest path
  * ALT algorithm for shortest path (requires pre-processed file)
  * Finding 5 nearest points of interest
  * Plotting routes and explored areas on the graphical map

  Note: The program requires ALT preprocessed data. If alt_landemerker.bin exists,
  ALT algorithm will use it. Otherwise, ALT will work as Dijkstra.
*/

class Graf {
  int N, K;
  double[] latRad;
  double[] lonRad;
  int[] poiMask;
  String[] poiName;
  HashMap<String, Integer> places = new HashMap<>();
  int[] head;
  int[] next;
  int[] to;
  int[] w;
  int[] dist;
  int[] prev;
  boolean[] expanded;
  long lastPopped = 0;
  int startnode = -1;
  int destnode = -1;
  int L = 0;
  int[] landmarks = new int[0];
  int[][] distFromL = new int[0][0];
  int[][] distToL = new int[0][0];
  static final int INF = 0x3f3f3f3f;

  String NODEFIL = "noder.txt";
  String KANTFIL = "kanter.txt";
  String POIFIL = "interessepkt.txt";
  String ALT_FIL = "alt_landemerker.bin";

  Graf(String dataDir) {
    if (dataDir != null && !dataDir.isEmpty()) {
      if (!dataDir.endsWith(File.separator)) dataDir += File.separator;
      NODEFIL = dataDir + NODEFIL;
      KANTFIL = dataDir + KANTFIL;
      POIFIL = dataDir + POIFIL;
      ALT_FIL = dataDir + ALT_FIL;
    }
  }

  void readNodes(String fil) throws IOException {
    try (BufferedReader br = new BufferedReader(
          new InputStreamReader(new FileInputStream(fil), StandardCharsets.UTF_8), 1<<20)) {
      String line = br.readLine();
      if (line == null) throw new IOException("Empty node file");
      N = Integer.parseInt(line.trim());
      latRad = new double[N];
      lonRad = new double[N];
      poiMask = new int[N];
      poiName = new String[N];
      Arrays.fill(poiMask, 0);

      for (int i = 0; i < N; i++) {
        line = br.readLine();
        if (line == null) throw new IOException("Unexpected end of node file at line " + (i + 2));
        line = line.trim();
        if (line.isEmpty()) { i--; continue; }

        String[] raw = line.split("[\t ]");
        ArrayList<String> fList = new ArrayList<>();
        for (String s : raw) if (!s.isEmpty()) fList.add(s);
        String[] f = fList.toArray(new String[0]);

        if (f.length < 3) { i--; continue; }

        int id = Integer.parseInt(f[0]);
        double lat = Double.parseDouble(f[1]);
        double lon = Double.parseDouble(f[2]);
        if (id < 0 || id >= N) throw new IOException("Node-id " + id + " out of range [0,"+N+")");
        latRad[id] = Math.toRadians(lat);
        lonRad[id] = Math.toRadians(lon);
      }
    }
  }

  void readEdges(String fil) throws IOException {
    try (BufferedReader br = new BufferedReader(
          new InputStreamReader(new FileInputStream(fil), StandardCharsets.UTF_8), 1<<20)) {
      String line = br.readLine();
      if (line == null) throw new IOException("Empty edge file");
      K = Integer.parseInt(line.trim());
      head = new int[N];
      Arrays.fill(head, -1);
      next = new int[K];
      to = new int[K];
      w = new int[K];
      int e = 0;
      while ((line = br.readLine()) != null) {
        if (line.isEmpty()) continue;
        String[] raw = line.split("[\t ]");
        ArrayList<String> fList = new ArrayList<>();
        for (String s : raw) if (!s.isEmpty()) fList.add(s);
        String[] f = fList.toArray(new String[0]);
        if (f.length < 5) continue;
        int u = Integer.parseInt(f[0]);
        int v = Integer.parseInt(f[1]);
        int time = Integer.parseInt(f[2]);
        if (u < 0 || u >= N || v < 0 || v >= N) continue;
        to[e] = v;
        w[e] = time;
        next[e] = head[u];
        head[u] = e;
        e++;
        if (e >= K) break;
      }
    }
  }

  void readPOI(String fil) throws IOException {
    if (!new File(fil).exists()) return;
    Pattern p = Pattern.compile("^(\\d+)\\s+(\\d+)\\s+\"(.*)\"$");
    try (BufferedReader br = new BufferedReader(
          new InputStreamReader(new FileInputStream(fil), StandardCharsets.UTF_8), 1<<20)) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;
        Matcher m = p.matcher(line);
        if (!m.matches()) continue;
        int id = Integer.parseInt(m.group(1));
        int code = Integer.parseInt(m.group(2));
        String name = m.group(3);
        if (0 <= id && id < N) {
          poiMask[id] = code;
          poiName[id] = name;
          places.put(name.toLowerCase(Locale.ROOT), id);
        }
      }
    }
  }

  void readALT(String fil) {
    File f = new File(fil);
    if (!f.exists()) return;
    try (DataInputStream in = new DataInputStream(
          new BufferedInputStream(new FileInputStream(f), 1<<20))) {
      int m0 = in.readUnsignedByte();
      int m1 = in.readUnsignedByte();
      int m2 = in.readUnsignedByte();
      int m3 = in.readUnsignedByte();
      if (!(m0 == 'A' && m1 == 'L' && m2 == 'T' && m3 == '1'))
        throw new IOException("ALT file has wrong magic number");
      int n = in.readInt();
      int l = in.readInt();
      if (n != N) throw new IOException("ALT N mismatch: " + n + " != " + N);
      L = l;

      landmarks = new int[L];
      for (int i = 0; i < L; i++) landmarks[i] = in.readInt();

      distFromL = new int[L][N];
      for (int i = 0; i < L; i++)
        for (int j = 0; j < N; j++) distFromL[i][j] = in.readInt();

      long header = 4  + 4  + 4  + 4L*L;
      long expect2 = header + 8L*L*N;

      long size = f.length();
      if (size >= expect2) {
        distToL = new int[L][N];
        for (int i = 0; i < L; i++)
          for (int j = 0; j < N; j++) distToL[i][j] = in.readInt();
      } else {
        distToL = new int[0][0];
      }
      System.out.println("Read ALT data: L=" + L + " (tables: " + (distToL.length == L ? "to/from" : "from-only") + ")");
      if (L == 0) {
        System.out.println("WARNING: ALT data file exists but contains no landmarks!");
      }
    } catch (Exception ex) {
      System.err.println("Could not read ALT data: " + ex.getMessage());
      System.err.println("ALT will work as regular Dijkstra (no heuristic)");
      L = 0;
      landmarks = new int[0];
      distFromL = new int[0][0];
      distToL = new int[0][0];
    }
    if (L == 0) {
      System.out.println("INFO: No ALT data available. ALT button will work as Dijkstra.");
    }
  }

  int popCount() { return (int)lastPopped; }

  boolean hasALT() { return L > 0 && distFromL.length == L; }

  int heuristicALT(int u, int g) {
    if (!hasALT()) return 0;
    int best = 0;
    for (int i = 0; i < L; i++) {
      int du_from = distFromL[i][u];
      int dg_from = distFromL[i][g];

      int h1 = 0;
      if (du_from < INF && dg_from < INF) {
        int diff = dg_from - du_from;
        if (diff > h1) h1 = diff;
      }

      int h2 = 0;
      if (distToL.length == L) {
        int du_to = distToL[i][u];
        int dg_to = distToL[i][g];
        if (du_to < INF && dg_to < INF) {
          int diff2 = du_to - dg_to;
          if (diff2 > h2) h2 = diff2;
        }
      }

      int h = Math.max(h1, h2);
      if (h > best) best = h;
    }
    return best;
  }

  static class PQNode {
    int v;
    int g;
    long f;
    PQNode(int v, int g, long f) { this.v = v; this.g = g; this.f = f; }
  }

  int shortestPath(int s, int t, boolean useALT) {
    if (s < 0 || t < 0 || s >= N || t >= N) return -1;
    startnode = s;
    destnode = t;

    if (dist == null || dist.length != N) {
      dist = new int[N];
      prev = new int[N];
    }
    if (expanded == null || expanded.length != N) expanded = new boolean[N];

    Arrays.fill(dist, INF);
    Arrays.fill(prev, -1);
    Arrays.fill(expanded, false);

    PriorityQueue<PQNode> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a.f));

    dist[s] = 0;
    long hStart = useALT ? (long)heuristicALT(s, t) : 0L;
    pq.add(new PQNode(s, 0, hStart));
    lastPopped = 0;

    while (!pq.isEmpty()) {
      PQNode cur = pq.poll();
      int u = cur.v;

      if (cur.g != dist[u]) continue;

      lastPopped++;
      expanded[u] = true;

      if (u == t) break;

      for (int e = head[u]; e != -1; e = next[e]) {
        int v = to[e];
        int nd = dist[u] + w[e];
        if (nd < dist[v]) {
          dist[v] = nd;
          prev[v] = u;
          long h = useALT ? (long)heuristicALT(v, t) : 0L;
          pq.add(new PQNode(v, nd, (long)nd + h));
        }
      }
    }
    return dist[t] >= INF/2 ? -1 : dist[t];
  }

  /**
   * Run a full Dijkstra from source s, filling distArray with distance to all nodes.
   * This is used only for ALT preprocessing.
   */
  void dijkstraAll(int s, int[] distArray) {
    if (distArray == null || distArray.length != N) {
      throw new IllegalArgumentException("distArray must have length N");
    }
    int[] d = new int[N];
    boolean[] visited = new boolean[N];
    Arrays.fill(d, INF);
    Arrays.fill(visited, false);

    PriorityQueue<PQNode> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a.f));
    d[s] = 0;
    pq.add(new PQNode(s, 0, 0L));

    while (!pq.isEmpty()) {
      PQNode cur = pq.poll();
      int u = cur.v;
      if (visited[u]) continue;
      visited[u] = true;

      for (int e = head[u]; e != -1; e = next[e]) {
        int v = to[e];
        int nd = d[u] + w[e];
        if (nd < d[v]) {
          d[v] = nd;
          pq.add(new PQNode(v, nd, nd));
        }
      }
    }
    System.arraycopy(d, 0, distArray, 0, N);
  }

  int[] selectLandmarks(int L) {
    if (L <= 0 || L > N) L = Math.min(16, N);

    int[] landmarks = new int[L];
    boolean[] selected = new boolean[N];

    int first = getFirst();
    landmarks[0] = first;
    selected[first] = true;

    Random rand = new Random(42);
    for (int l = 1; l < L; l++) {
      int best = -1;
      double maxMinDist = -1;

      for (int trial = 0; trial < Math.min(1000, N); trial++) {
        int candidate = rand.nextInt(N);
        if (selected[candidate]) continue;

        double minDistToLandmarks = Double.MAX_VALUE;
        for (int prev = 0; prev < l; prev++) {
          int lm = landmarks[prev];
          double dLat = latRad[candidate] - latRad[lm];
          double dLon = lonRad[candidate] - lonRad[lm];
          double dist = Math.sqrt(dLat * dLat + dLon * dLon);
          minDistToLandmarks = Math.min(minDistToLandmarks, dist);
        }

        if (minDistToLandmarks > maxMinDist) {
          maxMinDist = minDistToLandmarks;
          best = candidate;
        }
      }

      if (best == -1) {
        for (int i = 0; i < N; i++) {
          if (!selected[i]) {
            best = i;
            break;
          }
        }
      }

      if (best >= 0) {
        landmarks[l] = best;
        selected[best] = true;
      } else {
        L = l;
        break;
      }
    }
    return Arrays.copyOf(landmarks, L);
  }

  private int getFirst() {
    double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
    double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
    for (int i = 0; i < N; i++) {
      if (latRad[i] != 0 || lonRad[i] != 0) {
        minLat = Math.min(minLat, latRad[i]);
        maxLat = Math.max(maxLat, latRad[i]);
        minLon = Math.min(minLon, lonRad[i]);
        maxLon = Math.max(maxLon, lonRad[i]);
      }
    }
    double centerLat = (minLat + maxLat) / 2;
    double centerLon = (minLon + maxLon) / 2;

    int first = 0;
    double minDist = Double.MAX_VALUE;
    for (int i = 0; i < N; i++) {
      double dLat = latRad[i] - centerLat;
      double dLon = lonRad[i] - centerLon;
      double dist = dLat * dLat + dLon * dLon;
      if (dist < minDist) {
        minDist = dist;
        first = i;
      }
    }
    return first;
  }

  List<Integer> findNearestPOI(int s, int mask, int k) {
    if (s < 0 || s >= N) return Collections.emptyList();
    if (dist == null || dist.length != N) {
      dist = new int[N];
      prev = new int[N];
    }
    Arrays.fill(dist, INF);
    Arrays.fill(prev, -1);

    PriorityQueue<PQNode> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a.f));
    dist[s] = 0;
    pq.add(new PQNode(s, 0, 0L));
    List<Integer> found = new ArrayList<>();
    lastPopped = 0;

    while (!pq.isEmpty()) {
      PQNode cur = pq.poll();
      int u = cur.v;
      if (cur.g != dist[u]) continue;

      lastPopped++;

      if ((poiMask[u] & mask) == mask) {
        found.add(u);
        if (found.size() >= k) break;
      }

      for (int e = head[u]; e != -1; e = next[e]) {
        int v = to[e];
        int nd = dist[u] + w[e];
        if (nd < dist[v]) {
          dist[v] = nd;
          prev[v] = u;
          pq.add(new PQNode(v, nd, nd));
        }
      }
    }
    return found;
  }

  List<Integer> findNearestPOI_OR(int s, int mask, int k) {
    if (s < 0 || s >= N) return Collections.emptyList();
    if (dist == null || dist.length != N) {
      dist = new int[N];
      prev = new int[N];
    }
    Arrays.fill(dist, INF);
    Arrays.fill(prev, -1);

    PriorityQueue<PQNode> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a.f));
    dist[s] = 0;
    pq.add(new PQNode(s, 0, 0L));
    List<Integer> found = new ArrayList<>();
    lastPopped = 0;

    while (!pq.isEmpty()) {
      PQNode cur = pq.poll();
      int u = cur.v;
      if (cur.g != dist[u]) continue;

      lastPopped++;

      if ((poiMask[u] & mask) != 0) {
        found.add(u);
        if (found.size() >= k) break;
      }

      for (int e = head[u]; e != -1; e = next[e]) {
        int v = to[e];
        int nd = dist[u] + w[e];
        if (nd < dist[v]) {
          dist[v] = nd;
          prev[v] = u;
          pq.add(new PQNode(v, nd, nd));
        }
      }
    }
    return found;
  }

  List<Integer> getRoute(int s, int t) {
    if (s < 0 || t < 0 || s >= N || t >= N) return Collections.emptyList();
    if (prev == null) return Collections.emptyList();
    ArrayList<Integer> rev = new ArrayList<>();
    int n = t;
    if (dist[t] >= INF/2) return Collections.emptyList();
    while (n != -1) {
      rev.add(n);
      if (n == s) break;
      n = prev[n];
    }
    if (n == -1) return Collections.emptyList();
    Collections.reverse(rev);
    return rev;
  }

}

class vindu extends JPanel implements ActionListener, DocumentListener, JMapViewerEventListener {
  JButton btn_dijkstra = new JButton("Dijkstra");
  JButton btn_alt = new JButton("ALT");
  JButton btn_poi = new JButton("Find 5 nearest POI");
  JButton btn_slutt = new JButton("Exit");
  JLabel lbl_fra = new JLabel();
  JLabel lbl_til = new JLabel();
  JTextField txt_fra = new JTextField(18);
  JTextField txt_til = new JTextField(18);
  JTextField txt_poi_start = new JTextField(15);
  JComboBox<String> cbo_poi_type = new JComboBox<>(new String[]{
        "Charging station", "Gas station", "Restaurant", "Bar",
        "Food or drink", "Accommodation", "Place name"});
  JLabel lbl_tur = new JLabel("—");
  JLabel lbl_alg = new JLabel("—");
  String prev_from = "";
  String prev_to = "";
  JPanel kart = new JPanel(new BorderLayout());

  private final JMapViewerTree treeMap;
  private final JLabel zoomLabel;
  private final JLabel zoomValue;

  private final JLabel mperpLabelValue;

  Graf G;

  Layer rutelag, areallag, poilag;

  public vindu(Graf parameterG) {
    super(new GridBagLayout());
    G = parameterG;
    GridBagConstraints c = new GridBagConstraints();
    GridBagConstraints hc =  new GridBagConstraints();
    GridBagConstraints vc =  new GridBagConstraints();

    c.insets = new Insets(2, 4, 2, 4);
    hc.insets = new Insets(2, 4, 2, 4);
    vc.insets = new Insets(2, 4, 2, 4);

    btn_dijkstra.setActionCommand("dijkstra");
    btn_dijkstra.setMnemonic(KeyEvent.VK_D);
    btn_alt.setActionCommand("alt");
    btn_poi.setActionCommand("poi");

    btn_dijkstra.addActionListener(this);
    btn_alt.addActionListener(this);
    btn_poi.addActionListener(this);
    btn_slutt.addActionListener(this);

    txt_fra.getDocument().addDocumentListener(this);
    txt_til.getDocument().addDocumentListener(this);

    hc.gridx = 0; hc.gridy = 1;
    hc.anchor = GridBagConstraints.EAST;
    hc.fill = GridBagConstraints.NONE;
    add(new JLabel("From:"), hc);

    c.gridx = 1; c.gridy = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 0.3;
    add(txt_fra, c);

    hc.gridx = 3; hc.gridy = 1;
    add(new JLabel("To:"), hc);

    c.gridx = 4; c.gridy = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 0.3;
    add(txt_til, c);

    hc.gridx = 0; hc.gridy = 2;
    hc.anchor = GridBagConstraints.EAST;
    add(new JLabel("Node:"), hc);

    vc.gridx = 1; vc.gridy = 2;
    vc.anchor = GridBagConstraints.WEST;
    vc.fill = GridBagConstraints.NONE;
    vc.weightx = 0;
    add(lbl_fra, vc);

    hc.gridx = 3; hc.gridy = 2;
    add(new JLabel("Node:"), hc);

    vc.gridx = 4; vc.gridy = 2;
    add(lbl_til, vc);

    c.weightx = 0;
    c.fill = GridBagConstraints.NONE;

    c.gridx = 0; c.gridy = 3;
    add(btn_dijkstra, c);

    c.gridx = 1;
    add(btn_alt, c);

    c.gridx = 2;
    add(btn_poi, c);

    hc.gridx = 0; hc.gridy = 4;
    add(new JLabel("POI from:"), hc);

    c.gridx = 1; c.gridy = 4;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 0.25;
    add(txt_poi_start, c);

    c.gridx = 2; c.gridy = 4;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 0.25;
    add(cbo_poi_type, c);

    vc.gridx = 1; vc.gridy = 5;
    vc.gridwidth = 4;
    vc.fill = GridBagConstraints.HORIZONTAL;
    vc.weightx = 1.0;
    add(lbl_tur, vc);

    vc.gridy = 6;
    add(lbl_alg, vc);

    c.gridx = 5; c.gridy = 7;
    c.fill = GridBagConstraints.NONE;
    c.weightx = 0;
    add(btn_slutt, c);

    c.gridx = 0; c.gridy = 8;
    c.gridwidth = 6;
    c.gridheight = 5;
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.0;
    c.weighty = 1.0;
    add(kart, c);

    treeMap = new JMapViewerTree("Layers");
    rutelag = treeMap.addLayer("route");
    areallag = treeMap.addLayer("explored area");
    poilag = treeMap.addLayer("points of interest");
    map().addJMVListener(this);

    JPanel panel = new JPanel(new BorderLayout());
    JPanel panelTop = new JPanel();
    JPanel panelBottom = new JPanel();
    JPanel helpPanel = new JPanel();

    JLabel mperpLabelName = new JLabel("meter/Pixel: ");
    mperpLabelValue = new JLabel(String.format("%s", map().getMeterPerPixel()));

    zoomLabel = new JLabel("Zoom level: ");
    zoomValue = new JLabel(String.format("%s", map().getZoom()));

    kart.add(panel, BorderLayout.NORTH);
    kart.add(helpPanel, BorderLayout.SOUTH);
    panel.add(panelTop, BorderLayout.NORTH);
    panel.add(panelBottom, BorderLayout.SOUTH);
    JLabel helpLabel = new JLabel("Move with right mouse button,\n zoom with left button or double-click.");
    helpPanel.add(helpLabel);
    JButton button = new JButton("setDisplayToFitMapMarkers");
    button.addActionListener(_ -> map().setDisplayToFitMapMarkers());
    JComboBox<TileSource> tileSourceSelector = new JComboBox<>(new TileSource[] {
          new OsmTileSource.Mapnik(),
          new OsmTileSource.TransportMap(),
          new BingAerialTileSource(),
    });
    tileSourceSelector.addItemListener(e -> map().setTileSource((TileSource) e.getItem()));
    JComboBox<TileLoader> tileLoaderSelector;
    tileLoaderSelector = new JComboBox<>(new TileLoader[] {new OsmTileLoader(map())});
    tileLoaderSelector.addItemListener(e -> map().setTileLoader((TileLoader) e.getItem()));
    map().setTileLoader((TileLoader) tileLoaderSelector.getSelectedItem());
    panelTop.add(tileSourceSelector);
    panelTop.add(tileLoaderSelector);
    final JCheckBox showMapMarker = new JCheckBox("Map markers visible");
    showMapMarker.setSelected(map().getMapMarkersVisible());
    showMapMarker.addActionListener(_ -> map().setMapMarkerVisible(showMapMarker.isSelected()));
    panelBottom.add(showMapMarker);
    final JCheckBox showTreeLayers = new JCheckBox("Tree Layers visible");
    showTreeLayers.addActionListener(_ -> treeMap.setTreeVisible(showTreeLayers.isSelected()));
    panelBottom.add(showTreeLayers);
    final JCheckBox showToolTip = new JCheckBox("ToolTip visible");
    showToolTip.addActionListener(_ -> map().setToolTipText(null));
    panelBottom.add(showToolTip);
    final JCheckBox showTileGrid = new JCheckBox("Tile grid visible");
    showTileGrid.setSelected(map().isTileGridVisible());
    showTileGrid.addActionListener(_ -> map().setTileGridVisible(showTileGrid.isSelected()));
    panelBottom.add(showTileGrid);
    final JCheckBox showZoomControls = new JCheckBox("Show zoom controls");
    showZoomControls.setSelected(map().getZoomControlsVisible());
    showZoomControls.addActionListener(_ -> map().setZoomControlsVisible(showZoomControls.isSelected()));
    panelBottom.add(showZoomControls);
    final JCheckBox scrollWrapEnabled = new JCheckBox("Scrollwrap enabled");
    scrollWrapEnabled.addActionListener(_ -> map().setScrollWrapEnabled(scrollWrapEnabled.isSelected()));
    panelBottom.add(scrollWrapEnabled);
    panelBottom.add(button);

    panelTop.add(zoomLabel);
    panelTop.add(zoomValue);
    panelTop.add(mperpLabelName);
    panelTop.add(mperpLabelValue);

    kart.add(treeMap, BorderLayout.CENTER);

    map().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          map().getAttribution().handleAttribution(e.getPoint(), true);
        }
      }
    });

    map().addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        Point p = e.getPoint();
        boolean cursorHand = map().getAttribution().handleAttributionCursor(p);
        if (cursorHand) {
          map().setCursor(new Cursor(Cursor.HAND_CURSOR));
        } else {
          map().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        if (showToolTip.isSelected() && map().getPosition(p) != null)
          map().setToolTipText(map().getPosition(p).toString());
      }
    });

  }

  public double radToDeg(double rad) {
    return rad / Math.PI * 180;
  }

  public void drawRoute() {
    List<Integer> route = G.getRoute(G.startnode, G.destnode);
    if (route == null || route.isEmpty()) return;
    for (int n : route) {
      MapMarkerDot marker = new MapMarkerDot(rutelag, radToDeg(G.latRad[n]), radToDeg(G.lonRad[n]));
      map().addMapMarker(marker);
    }
  }

  public void drawArea() {
    if (G.expanded == null) return;
    for (int i = 0; i < G.N; i++) {
      if (G.expanded[i] && i != G.startnode && i != G.destnode) {
        MapMarkerDot marker = new MapMarkerDot(areallag, radToDeg(G.latRad[i]), radToDeg(G.lonRad[i]));
        marker.setBackColor(new Color(0, 0, 255, 70));
        map().addMapMarker(marker);
      }
    }
  }

  public void actionPerformed(ActionEvent e) {
    if ("dijkstra".equals(e.getActionCommand()) || "alt".equals(e.getActionCommand())) {
      int nodes;
      Date time1 = new Date();
      String trip = "Route " + txt_fra.getText() + " — " + txt_til.getText();
      String alg;

      int s = G.startnode;
      int t = G.destnode;
      if (s < 0 || t < 0) {
        lbl_tur.setText("Invalid from/to node");
        return;
      }

      boolean useALT = "alt".equals(e.getActionCommand());
      if (useALT && !G.hasALT()) {
        System.out.println("WARNING: No ALT data loaded! ALT will run with zero heuristic (same as Dijkstra).");
        System.out.println("To use ALT effectively, run ALT preprocessing first to generate alt_landemerker.bin");
      }
      int best = G.shortestPath(s, t, useALT);
      nodes = G.popCount();
      Date time2 = new Date();
      map().removeAllMapMarkers();

      if (best < 0) {
        trip += "  Path not found!";
      } else {
        int time = best;
        int tt = time / 360000; time -= 360000 * tt;
        int mm = time / 6000; time -= 6000 * mm;
        int ss = time / 100;
        int hs = time % 100;
        trip = String.format(Locale.ROOT, "%s Travel time %d:%02d:%02d,%02d", trip, tt, mm, ss, hs);
        drawRoute();
        drawArea();
      }
      float sec = (float)(time2.getTime() - time1.getTime()) / 1000;
      String algName = useALT ?
            (G.hasALT() ? "ALT algorithm" : "ALT algorithm (no data, using Dijkstra)") :
            "Dijkstra's algorithm";
      alg = String.format(Locale.ROOT, "%s processed %,d nodes in %.3fs. %.0f nodes/ms",
            algName, nodes, sec, nodes/sec/1000);
      lbl_tur.setText(trip);
      lbl_alg.setText(alg);
      System.out.println(trip);
      System.out.println(alg);
      System.out.println();
    } else if ("poi".equals(e.getActionCommand())) {
      String startTxt = txt_poi_start.getText().trim();
      if (startTxt.isEmpty()) {
        startTxt = txt_fra.getText().trim();
      }

      int startNode = -1;
      if (startTxt.matches("[0-9]+")) {
        startNode = Integer.parseInt(startTxt);
      } else {
        Integer I = G.places.get(startTxt.toLowerCase(Locale.ROOT));
        if (I != null) {
          startNode = I;
        }
      }

      if (startNode < 0 || startNode >= G.N) {
        lbl_tur.setText("Invalid start node for POI search");
        return;
      }
      int mask;
      boolean useOR = false;
      int idx = cbo_poi_type.getSelectedIndex();
      switch (idx) {
        case 0: // "Charging station"
          mask = 4;
          break;
        case 1: // "Gas station"
          mask = 2;
          break;
        case 2: // "Restaurant"
          mask = 8;
          break;
        case 3: // "Bar"
          mask = 16;
          break;
        case 4: // "Food or drink" = Restaurant OR Bar
          mask = 8 | 16;
          useOR = true;
          break;
        case 5: // "Accommodation"
          mask = 32;
          break;
        case 6: // "Place name"
          mask = 1;
          break;
        default:
          mask = 0;
      }

      if (mask == 0) {
        lbl_tur.setText("Invalid POI type");
        return;
      }

      Date time1 = new Date();
      List<Integer> poiList = useOR ?
            G.findNearestPOI_OR(startNode, mask, 5) :
            G.findNearestPOI(startNode, mask, 5);
      int nodes = G.popCount();
      Date time2 = new Date();

      map().removeAllMapMarkers();

      MapMarkerDot startMarker = new MapMarkerDot(rutelag, radToDeg(G.latRad[startNode]), radToDeg(G.lonRad[startNode]));
      startMarker.setBackColor(new Color(0, 255, 0, 200));
      startMarker.setName("Start: " + (G.poiName != null && G.poiName[startNode] != null ? G.poiName[startNode] : "node " + startNode));
      map().addMapMarker(startMarker);

      StringBuilder poiNames = new StringBuilder();
      for (int i = 0; i < poiList.size(); i++) {
        int poiId = poiList.get(i);
        int dist = G.dist[poiId];

        MapMarkerDot poiMarker = new MapMarkerDot(poilag, radToDeg(G.latRad[poiId]), radToDeg(G.lonRad[poiId]));
        poiMarker.setBackColor(new Color(255, 128, 0, 200));

        String poiName = (G.poiName != null && G.poiName[poiId] != null) ? G.poiName[poiId] : "POI";
        int tt = dist / 360000; int rem = dist % 360000;
        int mm = rem / 6000; rem = rem % 6000;
        int ss = rem / 100;
        String name = String.format(Locale.ROOT, "%s [%d] %d:%02d:%02d", poiName, poiId, tt, mm, ss);
        poiMarker.setName(name);
        map().addMapMarker(poiMarker);

        if (i > 0) poiNames.append(", ");
        poiNames.append(poiName).append(" [").append(poiId).append("]");
      }

      float sec = (float)(time2.getTime() - time1.getTime()) / 1000;
      String trip = String.format(Locale.ROOT, "5 nearest from node %d: %s", startNode, poiNames);
      String alg = String.format(Locale.ROOT, "POI search: %,d nodes in %.3fs", nodes, sec);

      lbl_tur.setText(trip);
      lbl_alg.setText(alg);
      System.out.println(trip);
      System.out.println(alg);
      System.out.println();

      map().setDisplayToFitMapMarkers();
    } else {
      System.exit(0);
    }
  }

  public void changedUpdate(DocumentEvent ev) { }
  public void removeUpdate(DocumentEvent ev) { lookupPlace(); }
  public void insertUpdate(DocumentEvent ev) { lookupPlace(); }

  void lookupPlace() {
    String txt = txt_fra.getText();
    if (!txt.equals(prev_from)) {
      prev_from = txt;
      if (txt.matches("[0-9]+")) {
        G.startnode = Integer.parseInt(txt);
      } else {
        Integer I = G.places.get(txt.toLowerCase(Locale.ROOT));
        G.startnode = Objects.requireNonNullElse(I, -1);
      }
      lbl_fra.setText(Integer.toString(G.startnode));
    }

    txt = txt_til.getText();
    if (!txt.equals(prev_to)) {
      prev_to = txt;
      if (txt.matches("[0-9]+")) {
        G.destnode = Integer.parseInt(txt);
      } else {
        Integer I = G.places.get(txt.toLowerCase(Locale.ROOT));
        G.destnode = Objects.requireNonNullElse(I, -1);
      }
      lbl_til.setText(Integer.toString(G.destnode));
    }

    boolean ready = (G.destnode >= 0 && G.startnode >= 0);
    btn_dijkstra.setEnabled(ready);
    btn_alt.setEnabled(ready);
  }

  public void processCommand(JMVCommandEvent command) {
    if (command.getCommand().equals(JMVCommandEvent.COMMAND.ZOOM) ||
          command.getCommand().equals(JMVCommandEvent.COMMAND.MOVE)) {
      updateZoomParameters();
    }
  }

  private void updateZoomParameters() {
    if (mperpLabelValue != null)
      mperpLabelValue.setText(String.format("%s", map().getMeterPerPixel()));
    if (zoomValue != null)
      zoomValue.setText(String.format("%s", map().getZoom()));
  }

  private JMapViewer map() {
    return treeMap.getViewer();
  }

}

public class Navi {

  public static void gui(Graf G) {
    JFrame frame = new JFrame("Map Navigation");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(new vindu(G));
    frame.pack();
    frame.setSize(1200, 800);
    frame.setVisible(true);
  }

  /**
   * ALT preprocessing logic (merged from PreprocessALT.main).
   */
  private static void runALTPreprocessing(String dataDir, int numLandmarks) {
    String baseDir = (dataDir != null && !dataDir.isEmpty()) ? dataDir : "." + File.separator;
    Graf G = new Graf(baseDir);

    try {
      System.out.println("Reading nodes from " + G.NODEFIL);
      long t1 = System.currentTimeMillis();
      G.readNodes(G.NODEFIL);
      long t2 = System.currentTimeMillis();
      System.out.println("Read " + G.N + " nodes in " + ((t2-t1)/1000.0) + " seconds");

      System.out.println("Reading edges from " + G.KANTFIL);
      t1 = System.currentTimeMillis();
      G.readEdges(G.KANTFIL);
      t2 = System.currentTimeMillis();
      System.out.println("Read " + G.K + " edges in " + ((t2-t1)/1000.0) + " seconds");

      System.out.println("\nSelecting " + numLandmarks + " landmarks...");
      t1 = System.currentTimeMillis();
      int[] landmarks = G.selectLandmarks(numLandmarks);
      int L = landmarks.length;
      t2 = System.currentTimeMillis();
      System.out.println("Selected " + L + " landmarks in " + ((t2-t1)/1000.0) + " seconds");
      System.out.print("Landmarks: ");
      for (int i = 0; i < Math.min(L, 10); i++) {
        System.out.print(landmarks[i] + " ");
      }
      if (L > 10) System.out.print("...");
      System.out.println();

      System.out.println("\nCalculating distances FROM landmarks to all nodes...");
      int[][] distFromL = new int[L][G.N];
      t1 = System.currentTimeMillis();
      for (int i = 0; i < L; i++) {
        System.out.print("Landmark " + (i+1) + "/" + L + " (node " + landmarks[i] + ")... ");
        long t3 = System.currentTimeMillis();
        G.dijkstraAll(landmarks[i], distFromL[i]);
        long t4 = System.currentTimeMillis();
        System.out.println(((t4-t3)/1000.0) + "s");
      }
      t2 = System.currentTimeMillis();
      System.out.println("Completed in " + ((t2-t1)/1000.0) + " seconds");

      System.out.println("\nCalculating distances TO landmarks from all nodes...");
      int[][] distToL = new int[L][G.N];
      t1 = System.currentTimeMillis();
      System.out.println("Building reversed graph...");
      int[] revHead = new int[G.N];
      int[] revNext = new int[G.K];
      int[] revTo = new int[G.K];
      int[] revW = new int[G.K];
      Arrays.fill(revHead, -1);
      int revE = 0;
      for (int u = 0; u < G.N; u++) {
        for (int e = G.head[u]; e != -1; e = G.next[e]) {
          int v = G.to[e];
          revTo[revE] = u;
          revW[revE] = G.w[e];
          revNext[revE] = revHead[v];
          revHead[v] = revE;
          revE++;
        }
      }

      int[] origHead = G.head;
      int[] origNext = G.next;
      int[] origTo = G.to;
      int[] origW = G.w;

      G.head = revHead;
      G.next = revNext;
      G.to = revTo;
      G.w = revW;

      for (int i = 0; i < L; i++) {
        System.out.print("Landmark " + (i+1) + "/" + L + " (node " + landmarks[i] + ")... ");
        long t3 = System.currentTimeMillis();
        G.dijkstraAll(landmarks[i], distToL[i]);
        long t4 = System.currentTimeMillis();
        System.out.println(((t4-t3)/1000.0) + "s");
      }

      G.head = origHead;
      G.next = origNext;
      G.to = origTo;
      G.w = origW;

      t2 = System.currentTimeMillis();
      System.out.println("Completed in " + ((t2-t1)/1000.0) + " seconds");

      System.out.println("\nSaving ALT data to " + G.ALT_FIL);
      t1 = System.currentTimeMillis();
      try (DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(G.ALT_FIL), 1<<20))) {
        out.writeByte('A');
        out.writeByte('L');
        out.writeByte('T');
        out.writeByte('1');
        out.writeInt(G.N);
        out.writeInt(L);
        for (int landmark : landmarks) {
          out.writeInt(landmark);
        }
        for (int i = 0; i < L; i++) {
          for (int j = 0; j < G.N; j++) {
            out.writeInt(distFromL[i][j]);
          }
        }
        for (int i = 0; i < L; i++) {
          for (int j = 0; j < G.N; j++) {
            out.writeInt(distToL[i][j]);
          }
        }
      }
      t2 = System.currentTimeMillis();
      System.out.println("Saved in " + ((t2-t1)/1000.0) + " seconds");

      long fileSize = new File(G.ALT_FIL).length();
      System.out.println("\nALT data file size: " + (fileSize / (1024*1024)) + " MB");
      System.out.println("Preprocessing completed!");

    } catch (IOException e) {
      System.err.println("Error during ALT preprocessing: " + e.getMessage());
    }
  }

  public static void main(String[] args) {
    String dataDir = (args != null && args.length > 0) ? args[0] : "." + File.separator;
    int numLandmarks = 16;
    if (args != null && args.length > 1) {
      try {
        numLandmarks = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        System.err.println("Invalid number of landmarks, using 16");
      }
    }

    System.out.println("Enter the number to choose an option:");
    System.out.println("1. Create the file of preprocessed data for ALT");
    System.out.println("2. Run the navigation program");

    int choice = -1;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
      String line = br.readLine();
      if (line != null) {
        try {
          choice = Integer.parseInt(line.trim());
        } catch (NumberFormatException ignored) {
        }
      }
    } catch (IOException e) {
      System.err.println("Error reading input: " + e.getMessage());
      return;
    }

    if (choice == 1) {
      runALTPreprocessing(dataDir, numLandmarks);
    } else if (choice == 2) {
      Graf G = new Graf(dataDir);
      try {
        System.out.println("Reading nodes from " + G.NODEFIL);
        G.readNodes(G.NODEFIL);
        System.out.println("Reading edges from " + G.KANTFIL);
        G.readEdges(G.KANTFIL);
        System.out.println("Reading POI from " + G.POIFIL);
        G.readPOI(G.POIFIL);
        G.readALT(G.ALT_FIL);
      } catch (IOException e) {
        System.err.println("Error reading data: " + e.getMessage());
      }
      gui(G);
    } else {
      System.out.println("Exiting.");
    }
  }
}
