package com.alpha4technologies.prayerclock;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TasbihListActivity extends BaseActivity {

    RecyclerView rv;
    FloatingActionButton fab;
    ArrayList<TasbihModel> tasbihList = new ArrayList<>();
    TasbihAdapter adapter;
    TextView tvTitle;

    SharedPreferences prefs;
    Gson gson = new Gson();
    private NavigationHelper navHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ===== Fullscreen + Transparent Status Bar =====
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setContentView(R.layout.activity_tasbih_list);

        rv = findViewById(R.id.rvTasbih);
        fab = findViewById(R.id.fabAdd);
        tvTitle = findViewById(R.id.tvTitle);
        TextView btnMenu = findViewById(R.id.btnMenu);

        // Back button function (btnMenu is reused as back)
        if (btnMenu != null) {
            btnMenu.setText("Back");
            btnMenu.setOnClickListener(v -> finish());
        }

        prefs = getSharedPreferences("TasbihPrefs", MODE_PRIVATE);

        // Load local saved list first
        loadTasbih();

        // Setup Adapter
        adapter = new TasbihAdapter(
                this,
                tasbihList,
                this::saveTasbih
        );

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // Sync from Firebase
        fetchFirebaseTasbihs();

        navHelper = new NavigationHelper(this, 2, true);
        navHelper.init();

        View root = findViewById(R.id.root_tasbih);
        root.setOnTouchListener((v, event) -> {
            navHelper.resetHideTimer();
            return false;
        });

        fab.setOnClickListener(v -> showAddDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTasbih();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void fetchFirebaseTasbihs() {
        try {
            // Check "items" node first (matching Firebase database structure)
            FirebaseDatabase.getInstance().getReference("items")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            List<TasbihModel> fbList = parseFirebaseSnapshot(snapshot);
                            if (!fbList.isEmpty()) {
                                mergeAndApplyTasbihs(fbList);
                            } else {
                                fetchFromTasbihListFallback();
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            fetchFromTasbihListFallback();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            mergeAndApplyTasbihs(new ArrayList<>());
        }
    }

    private void fetchFromTasbihListFallback() {
        try {
            FirebaseDatabase.getInstance().getReference("tasbihList")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            List<TasbihModel> fbList = parseFirebaseSnapshot(snapshot);
                            mergeAndApplyTasbihs(fbList);
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            mergeAndApplyTasbihs(new ArrayList<>());
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            mergeAndApplyTasbihs(new ArrayList<>());
        }
    }

    private List<TasbihModel> parseFirebaseSnapshot(DataSnapshot snapshot) {
        List<TasbihModel> fbList = new ArrayList<>();
        if (snapshot.exists() && snapshot.hasChildren()) {
            for (DataSnapshot child : snapshot.getChildren()) {
                String name = null;
                String content = "";
                String id = "fb_" + child.getKey();

                if (child.getValue() instanceof String) {
                    name = child.getValue(String.class);
                } else {
                    if (child.hasChild("name")) {
                        name = child.child("name").getValue(String.class);
                    } else if (child.hasChild("title")) {
                        name = child.child("title").getValue(String.class);
                    }

                    if (child.hasChild("content")) {
                        content = child.child("content").getValue(String.class);
                    } else if (child.hasChild("text")) {
                        content = child.child("text").getValue(String.class);
                    } else if (child.hasChild("tasbihText")) {
                        content = child.child("tasbihText").getValue(String.class);
                    } else if (child.hasChild("arabic")) {
                        content = child.child("arabic").getValue(String.class);
                    }

                    if (child.child("id").exists() && child.child("id").getValue() != null) {
                        id = child.child("id").getValue(String.class);
                    }
                }

                if (name != null && !name.trim().isEmpty()) {
                    fbList.add(new TasbihModel(id, name.trim(), content != null ? content.trim() : "", 0, false));
                }
            }
        }
        return fbList;
    }

    private void mergeAndApplyTasbihs(List<TasbihModel> fbList) {
        // If local list is empty and Firebase provided nothing, populate default Tasbihs
        if (tasbihList.isEmpty() && fbList.isEmpty()) {
            fbList.add(new TasbihModel("default_1", "تسبیح فاطمی", "سُبْحَانَ اللَّهِ", 0, false));
            fbList.add(new TasbihModel("default_2", "تحمید", "الْحَمْدُ لِلَّهِ", 0, false));
            fbList.add(new TasbihModel("default_3", "تکبیر", "اللَّهُ أَكْبَرُ", 0, false));
            fbList.add(new TasbihModel("default_4", "استغفار", "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", 0, false));
            fbList.add(new TasbihModel("default_5", "توحید", "لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ", 0, false));
            fbList.add(new TasbihModel("default_6", "درود شریف", "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ", 0, false));
            fbList.add(new TasbihModel("default_7", "حوقلہ", "لاَ حَوْلَ وَلاَ قُوَّةَ إِلاَّ بِاللَّهِ الْعَلِيِّ الْعَظِيمِ", 0, false));
        }

        // Add any missing Firebase Tasbihs to the existing list & update text if missing
        for (TasbihModel fbItem : fbList) {
            boolean exists = false;
            for (TasbihModel existing : tasbihList) {
                if ((existing.id != null && existing.id.equals(fbItem.id))
                        || (existing.name != null && existing.name.trim().equalsIgnoreCase(fbItem.name.trim()))) {
                    exists = true;
                    if ((existing.content == null || existing.content.trim().isEmpty())
                            && fbItem.content != null && !fbItem.content.trim().isEmpty()) {
                        existing.content = fbItem.content;
                    }
                    break;
                }
            }
            if (!exists) {
                tasbihList.add(fbItem);
            }
        }

        saveTasbih();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void showAddDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_add_tasbih);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText etName = dialog.findViewById(R.id.etTasbihName);
        EditText etContent = dialog.findViewById(R.id.etTasbihContent);
        Button btnAdd = dialog.findViewById(R.id.btnAdd);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String content = etContent != null ? etContent.getText().toString().trim() : "";
            if (name.isEmpty()) {
                etName.setError("Please enter a name");
                return;
            }

            String id = "custom_" + UUID.randomUUID().toString();
            // User custom Tasbih created locally (isCustom = true)
            TasbihModel t = new TasbihModel(id, name, content, 0, true);

            tasbihList.add(t);
            adapter.notifyItemInserted(tasbihList.size() - 1);
            saveTasbih();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveTasbih() {
        prefs.edit()
                .putString("list", gson.toJson(tasbihList))
                .apply();
    }

    private void loadTasbih() {
        String json = prefs.getString("list", null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<TasbihModel>>() {}.getType();
            ArrayList<TasbihModel> loadedList = gson.fromJson(json, type);
            if (loadedList != null && !loadedList.isEmpty()) {
                tasbihList.clear();
                tasbihList.addAll(loadedList);
            }
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}
