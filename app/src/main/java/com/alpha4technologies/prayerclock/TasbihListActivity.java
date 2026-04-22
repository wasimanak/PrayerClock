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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.UUID;

public class TasbihListActivity extends AppCompatActivity {

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

        loadTasbih();

        // ✅ Adapter init with Context (3 arguments)
        adapter = new TasbihAdapter(
                this,          // Context
                tasbihList,
                this::saveTasbih
        );

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

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
        // Reload list in case count was updated in TasbihCounterActivity
        loadTasbih();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void showAddDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_add_tasbih);
        
        // Make dialog background transparent for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText et = dialog.findViewById(R.id.etTasbihName);
        Button btnAdd = dialog.findViewById(R.id.btnAdd);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) {
                et.setError("Please enter a name");
                return;
            }

            String id = UUID.randomUUID().toString();
            TasbihModel t = new TasbihModel(id, name, 0);

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
            if (loadedList != null) {
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
