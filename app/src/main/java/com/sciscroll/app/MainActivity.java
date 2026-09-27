package com.sciscroll.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String API = "https://www.ebi.ac.uk/europepmc/webservices/rest/search";
    private static final String PREFS = "sciscroll_prefs";
    private static final String SAVED = "saved_articles";
    private static final String IGNORED = "ignored_articles";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Article> articles = new ArrayList<>();
    private final Map<String, Button> topicButtons = new LinkedHashMap<>();

    private SharedPreferences prefs;
    private LinearLayout root;
    private ViewFlipper flipper;
    private ProgressBar loading;
    private TextView status;
    private LinearLayout topicRow;
    private GestureDetector gestures;
    private String activeTopic = "Biotechnology";
    private int currentIndex = 0;
    private boolean searchMode = false;

    private final Map<String, String> topics = new LinkedHashMap<String, String>() {{
        put("Biotech", "biotechnology OR biomaterials OR tissue engineering");
        put("Neuroscience", "neuroscience OR brain OR neurobiology");
        put("Medicine", "medicine OR clinical research");
        put("Genetics", "genetics OR genomics OR gene editing");
        put("AI", "artificial intelligence OR machine learning");
        put("General", "OPEN_ACCESS:y");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        setupGestures();
        loadTopic("Biotech", false);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c("#0A0F16"));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(12), dp(8));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        TextView title = text("SciScroll", 25, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button search = pill("Search");
        search.setOnClickListener(v -> showSearchDialog());
        header.addView(search);

        HorizontalScrollView topicsScroll = new HorizontalScrollView(this);
        topicsScroll.setHorizontalScrollBarEnabled(false);
        topicRow = new LinearLayout(this);
        topicRow.setOrientation(LinearLayout.HORIZONTAL);
        topicRow.setPadding(dp(12), dp(4), dp(12), dp(10));
        topicsScroll.addView(topicRow);
        root.addView(topicsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        rebuildTopicButtons();

        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        flipper = new ViewFlipper(this);
        stage.addView(flipper, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(54), dp(54));
        lp.gravity = Gravity.CENTER;
        stage.addView(loading, lp);

        status = text("Loading papers…", 14, c("#AAB8C8"), false);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        sp.bottomMargin = dp(18);
        stage.addView(status, sp);

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(8), dp(8), dp(10));
        nav.setGravity(Gravity.CENTER);
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        Button feed = navButton("Feed");
        feed.setOnClickListener(v -> {
            searchMode = false;
            rebuildTopicButtons();
            loadTopic(activeTopic.equals("Biotechnology") ? "Biotech" : topicKeyForQuery(activeTopic), false);
        });
        nav.addView(feed, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button saved = navButton("Saved");
        saved.setOnClickListener(v -> showSaved());
        nav.addView(saved, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button collections = navButton("Collections");
        collections.setOnClickListener(v -> showCollections());
        nav.addView(collections, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private void rebuildTopicButtons() {
        topicRow.removeAllViews();
        topicButtons.clear();
        for (String key : topics.keySet()) {
            Button b = pill(key);
            b.setOnClickListener(v -> loadTopic(key, false));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            p.setMargins(dp(4), 0, dp(4), 0);
            topicRow.addView(b, p);
            topicButtons.put(key, b);
        }
    }

    private void setupGestures() {
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > dp(70) && Math.abs(velocityY) > 450) {
                    if (dy < 0) showNext(); else showPrevious();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures != null && gestures.onTouchEvent(ev)) return true;
        return super.dispatchTouchEvent(ev);
    }

    private void loadTopic(String key, boolean append) {
        String query = topics.get(key);
        if (query == null) query = key;
        activeTopic = key;
        searchMode = false;
        fetch(query + " sort_date:y", append, key);
    }

    private String topicKeyForQuery(String value) {
        if (topics.containsKey(value)) return value;
        return "Biotech";
    }

    private void fetch(String query, boolean append, String label) {
        showLoading(true, "Finding " + label + " papers…");
        executor.execute(() -> {
            List<Article> found = new ArrayList<>();
            String error = null;
            try {
                String url = API + "?query=" + URLEncoder.encode(query, "UTF-8")
                        + "&format=json&resultType=core&pageSize=35";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(18000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "SciScroll/0.1 (Android; literature discovery app)");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("Server returned " + code);
                String body = readAll(conn.getInputStream());
                JSONObject json = new JSONObject(body);
                JSONArray results = json.getJSONObject("resultList").getJSONArray("result");
                Set<String> ignored = prefs.getStringSet(IGNORED, new HashSet<>());
                for (int i = 0; i < results.length(); i++) {
                    Article a = Article.from(results.getJSONObject(i));
                    if (a.title.trim().isEmpty() || ignored.contains(a.key())) continue;
                    found.add(a);
                }
                conn.disconnect();
            } catch (Exception e) {
                error = e.getMessage() == null ? "Network error" : e.getMessage();
            }
            final String finalError = error;
            runOnUiThread(() -> {
                if (!append) articles.clear();
                articles.addAll(found);
                currentIndex = 0;
                renderAll();
                showLoading(false, articles.isEmpty() ? "No papers found" : (articles.size() + " papers • swipe up"));
                if (finalError != null && articles.isEmpty()) {
                    showOfflineWelcome(finalError);
                }
            });
        });
    }

    private String readAll(InputStream stream) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void renderAll() {
        flipper.removeAllViews();
        for (Article a : articles) flipper.addView(articleCard(a));
        if (!articles.isEmpty()) flipper.setDisplayedChild(Math.min(currentIndex, articles.size() - 1));
    }

    private View articleCard(Article a) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(c("#0A0F16"));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(28));
        scroll.addView(card, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = text(a.topicBadge(), 12, c("#62D7B2"), true);
        card.addView(badge);

        TextView title = text(a.title, 26, Color.WHITE, true);
        title.setPadding(0, dp(12), 0, dp(10));
        card.addView(title);

        String meta = joinMeta(a.author, a.journal, a.year);
        TextView metaTv = text(meta, 14, c("#AAB8C8"), false);
        metaTv.setPadding(0, 0, 0, dp(18));
        card.addView(metaTv);

        card.addView(sectionLabel("30-SECOND SUMMARY"));
        TextView summary = text(a.summary(), 18, c("#E8EEF5"), false);
        summary.setLineSpacing(0, 1.16f);
        summary.setPadding(0, dp(8), 0, dp(18));
        card.addView(summary);

        card.addView(sectionLabel("WHY IT MAY MATTER"));
        TextView why = text(a.whyItMatters(), 16, c("#D4DEE8"), false);
        why.setLineSpacing(0, 1.12f);
        why.setPadding(0, dp(8), 0, dp(18));
        card.addView(why);

        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.HORIZONTAL);
        facts.setPadding(0, dp(4), 0, dp(16));
        facts.addView(stat("Citations", a.citedBy), new LinearLayout.LayoutParams(0, dp(68), 1));
        facts.addView(stat("Open access", a.openAccess ? "Yes" : "Unknown"), new LinearLayout.LayoutParams(0, dp(68), 1));
        facts.addView(stat("Year", a.year.isEmpty() ? "—" : a.year), new LinearLayout.LayoutParams(0, dp(68), 1));
        card.addView(facts);

        LinearLayout actions1 = new LinearLayout(this);
        actions1.setOrientation(LinearLayout.HORIZONTAL);
        Button save = actionButton(isSaved(a) ? "Saved ✓" : "Save");
        save.setOnClickListener(v -> {
            toggleSaved(a);
            save.setText(isSaved(a) ? "Saved ✓" : "Save");
        });
        actions1.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button open = actionButton("Open paper");
        open.setOnClickListener(v -> openPaper(a));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, dp(50), 1); op.setMargins(dp(10),0,0,0);
        actions1.addView(open, op);
        card.addView(actions1);

        LinearLayout actions2 = new LinearLayout(this);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        actions2.setPadding(0, dp(10), 0, 0);
        Button cite = secondaryButton("Copy citation");
        cite.setOnClickListener(v -> copyCitation(a));
        actions2.addView(cite, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button share = secondaryButton("Share");
        share.setOnClickListener(v -> shareArticle(a));
        LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(0, dp(46), 1); shp.setMargins(dp(8),0,0,0);
        actions2.addView(share, shp);
        Button nope = secondaryButton("Not interested");
        nope.setOnClickListener(v -> ignoreArticle(a));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(46), 1); np.setMargins(dp(8),0,0,0);
        actions2.addView(nope, np);
        card.addView(actions2);

        TextView swipe = text("↑ Swipe for the next paper", 13, c("#718195"), false);
        swipe.setGravity(Gravity.CENTER);
        swipe.setPadding(0, dp(24), 0, dp(4));
        card.addView(swipe);
        return scroll;
    }

    private View stat(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(5), dp(4), dp(5));
        box.setBackground(roundRect("#111A24", 18));
        box.addView(text(value, 17, Color.WHITE, true));
        box.addView(text(label, 11, c("#AAB8C8"), false));
        return box;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 12, c("#62D7B2"), true);
        t.setLetterSpacing(0.07f);
        return t;
    }

    private void showNext() {
        if (articles.isEmpty()) return;
        if (currentIndex >= articles.size() - 1) {
            Toast.makeText(this, "You reached the end of this batch", Toast.LENGTH_SHORT).show();
            return;
        }
        currentIndex++;
        flipper.setInAnimation(slide(1, true));
        flipper.setOutAnimation(slide(-1, true));
        flipper.showNext();
        status.setText((currentIndex + 1) + " / " + articles.size());
    }

    private void showPrevious() {
        if (articles.isEmpty() || currentIndex <= 0) return;
        currentIndex--;
        flipper.setInAnimation(slide(-1, false));
        flipper.setOutAnimation(slide(1, false));
        flipper.showPrevious();
        status.setText((currentIndex + 1) + " / " + articles.size());
    }

    private Animation slide(int direction, boolean upward) {
        float from = direction > 0 ? 1f : -1f;
        TranslateAnimation a = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, from, Animation.RELATIVE_TO_SELF, 0);
        a.setDuration(230);
        return a;
    }

    private void showLoading(boolean yes, String message) {
        loading.setVisibility(yes ? View.VISIBLE : View.GONE);
        status.setText(message);
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("e.g. peptide hydrogel neural regeneration");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Search scientific papers")
                .setView(input)
                .setPositiveButton("Search", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) {
                        searchMode = true;
                        fetch(q + " sort_date:y", false, q);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSaved() {
        Set<String> raw = prefs.getStringSet(SAVED, new HashSet<>());
        articles.clear();
        for (String s : raw) {
            try { articles.add(Article.fromStored(new JSONObject(s))); } catch (Exception ignored) {}
        }
        currentIndex = 0;
        renderAll();
        showLoading(false, articles.isEmpty() ? "No saved papers yet" : articles.size() + " saved papers");
    }

    private void showCollections() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        TextView copy = text("Collections are intentionally simple in this first build. Your Saved feed is your default reading list.", 16, c("#263238"), false);
        box.addView(copy);
        new AlertDialog.Builder(this)
                .setTitle("Collections")
                .setView(box)
                .setPositiveButton("Open Saved", (d,w) -> showSaved())
                .setNegativeButton("Close", null)
                .show();
    }

    private boolean isSaved(Article a) {
        Set<String> raw = prefs.getStringSet(SAVED, new HashSet<>());
        for (String s : raw) {
            try {
                if (new JSONObject(s).optString("key").equals(a.key())) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void toggleSaved(Article a) {
        Set<String> current = new HashSet<>(prefs.getStringSet(SAVED, new HashSet<>()));
        String match = null;
        for (String s : current) {
            try { if (new JSONObject(s).optString("key").equals(a.key())) match = s; } catch (Exception ignored) {}
        }
        if (match != null) {
            current.remove(match);
            Toast.makeText(this, "Removed from Saved", Toast.LENGTH_SHORT).show();
        } else {
            current.add(a.toStored().toString());
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet(SAVED, current).apply();
    }

    private void ignoreArticle(Article a) {
        Set<String> ignored = new HashSet<>(prefs.getStringSet(IGNORED, new HashSet<>()));
        ignored.add(a.key());
        prefs.edit().putStringSet(IGNORED, ignored).apply();
        int idx = articles.indexOf(a);
        if (idx >= 0) articles.remove(idx);
        if (currentIndex >= articles.size()) currentIndex = Math.max(0, articles.size() - 1);
        renderAll();
        status.setText(articles.isEmpty() ? "No papers left in this batch" : (currentIndex + 1) + " / " + articles.size());
    }

    private void openPaper(Article a) {
        String url = a.url();
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show(); }
    }

    private void copyCitation(Article a) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Citation", a.citation()));
        Toast.makeText(this, "Citation copied", Toast.LENGTH_SHORT).show();
    }

    private void shareArticle(Article a) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, a.title + "\n" + a.url());
        startActivity(Intent.createChooser(i, "Share paper"));
    }

    private void showOfflineWelcome(String detail) {
        articles.clear();
        articles.add(Article.welcome());
        renderAll();
        status.setText("Offline demo • " + detail);
    }

    private String joinMeta(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null || v.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(v.trim());
        }
        return sb.toString();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button pill(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(roundRect("#172331", 50));
        return b;
    }

    private Button navButton(String label) {
        Button b = pill(label);
        b.setBackground(roundRect("#111A24", 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        p.setMargins(dp(4),0,dp(4),0);
        b.setLayoutParams(p);
        return b;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(c("#06120F"));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundRect("#62D7B2", 18));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(c("#DDE6EF"));
        b.setBackground(roundRect("#172331", 16));
        return b;
    }

    private android.graphics.drawable.GradientDrawable roundRect(String hex, int radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(c(hex)); g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int c(String hex) { return Color.parseColor(hex); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    static class Article {
        String source="", id="", title="", author="", journal="", year="", abstractText="", doi="", citedBy="0";
        boolean openAccess=false;

        static Article from(JSONObject o) {
            Article a = new Article();
            a.source = o.optString("source", "");
            a.id = firstNonEmpty(o.optString("id", ""), o.optString("pmid", ""), o.optString("pmcid", ""));
            a.title = clean(o.optString("title", ""));
            a.author = clean(o.optString("authorString", ""));
            a.journal = clean(firstNonEmpty(o.optString("journalTitle", ""), o.optString("journalInfo", "")));
            a.year = clean(firstNonEmpty(o.optString("pubYear", ""), yearOf(o.optString("firstPublicationDate", ""))));
            a.abstractText = cleanHtml(o.optString("abstractText", ""));
            a.doi = clean(o.optString("doi", ""));
            a.citedBy = String.valueOf(o.optInt("citedByCount", 0));
            a.openAccess = "Y".equalsIgnoreCase(o.optString("isOpenAccess", "")) || o.optBoolean("isOpenAccess", false);
            return a;
        }

        static Article fromStored(JSONObject o) {
            Article a = new Article();
            a.source=o.optString("source"); a.id=o.optString("id"); a.title=o.optString("title");
            a.author=o.optString("author"); a.journal=o.optString("journal"); a.year=o.optString("year");
            a.abstractText=o.optString("abstract"); a.doi=o.optString("doi"); a.citedBy=o.optString("citedBy","0");
            a.openAccess=o.optBoolean("openAccess",false); return a;
        }

        static Article welcome() {
            Article a = new Article();
            a.source = "SCROLL"; a.id = "welcome";
            a.title = "Welcome to SciScroll";
            a.author = "Live scientific discovery feed";
            a.journal = "Europe PMC powered";
            a.year = "";
            a.abstractText = "SciScroll turns scientific literature into a vertical discovery feed. Swipe up for the next paper, search by topic, save papers for later, copy a citation, share an article, or open the original record. Connect to the internet to load live papers and abstracts.";
            a.openAccess = true;
            return a;
        }

        String key() { return (source + ":" + id + ":" + title).toLowerCase(Locale.ROOT); }

        JSONObject toStored() {
            JSONObject o = new JSONObject();
            try {
                o.put("key", key()); o.put("source",source); o.put("id",id); o.put("title",title);
                o.put("author",author); o.put("journal",journal); o.put("year",year); o.put("abstract",abstractText);
                o.put("doi",doi); o.put("citedBy",citedBy); o.put("openAccess",openAccess);
            } catch (JSONException ignored) {}
            return o;
        }

        String summary() {
            if (abstractText == null || abstractText.trim().isEmpty()) {
                return "No abstract was supplied for this record. Open the paper to read the full article or publisher summary.";
            }
            String t = abstractText.trim();
            String[] sentences = t.split("(?<=[.!?])\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<sentences.length && i<3; i++) {
                if (sb.length()>0) sb.append(" "); sb.append(sentences[i]);
                if (sb.length()>620) break;
            }
            if (sb.length() == 0) return crop(t, 620);
            return crop(sb.toString(), 680);
        }

        String whyItMatters() {
            if (abstractText == null || abstractText.trim().isEmpty()) {
                return "Use the title, journal, citation count and full article to judge relevance. SciScroll does not treat a missing abstract as evidence for or against a paper's conclusions.";
            }
            String[] s = abstractText.split("(?<=[.!?])\\s+");
            if (s.length > 0) {
                String last = s[s.length-1].trim();
                if (last.length() > 50) return crop(last, 430) + "  — Check the methods and limitations before relying on the finding.";
            }
            return "This paper may be relevant to the topic you selected. Open the full article to check the methods, sample, limitations and whether the evidence supports the conclusion.";
        }

        String topicBadge() {
            if (openAccess) return "OPEN ACCESS  •  PAPER";
            return "SCIENTIFIC PAPER";
        }

        String url() {
            if (!doi.isEmpty()) return "https://doi.org/" + doi;
            if (!source.isEmpty() && !id.isEmpty()) return "https://europepmc.org/article/" + source + "/" + id;
            return "https://europepmc.org/";
        }

        String citation() {
            StringBuilder sb = new StringBuilder();
            if (!author.isEmpty()) sb.append(author).append(" ");
            if (!year.isEmpty()) sb.append("(").append(year).append("). ");
            sb.append(title);
            if (!journal.isEmpty()) sb.append(". ").append(journal);
            if (!doi.isEmpty()) sb.append(". https://doi.org/").append(doi);
            return sb.toString();
        }

        static String firstNonEmpty(String... values) {
            for (String s: values) if (s != null && !s.trim().isEmpty()) return s;
            return "";
        }
        static String yearOf(String s) { return s != null && s.length()>=4 ? s.substring(0,4) : ""; }
        static String clean(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }
        static String cleanHtml(String s) { return clean(s == null ? "" : s.replaceAll("<[^>]+>", " ").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&")); }
        static String crop(String s, int max) { return s.length() <= max ? s : s.substring(0, max).trim() + "…"; }
    }
}
