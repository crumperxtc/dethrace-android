package org.dethrace.carmageddon;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Launcher and game menu. Each game (Carmageddon, Splat Pack) lives in its own
 * folder under the app's files directory. The player adds game files by
 * picking a folder or ZIP; the game is recognised from its data and DATA and
 * MUSIC are copied into that game's folder. Play starts CarmageddonActivity
 * with the folder, which android_main.c points dethrace at.
 */
public class SetupActivity extends Activity {
    static final String EXTRA_GAME = "game";

    private static final int PICK_FOLDER = 1;
    private static final int PICK_ZIP = 2;

    private static final String[] GAME_IDS = { "carmageddon", "splatpack" };
    private static final String[] GAME_NAMES = { "Carmageddon", "Splat Pack" };

    private File root;
    private LinearLayout gameList;
    private TextView status;
    private ProgressBar progress;
    private Button folderButton;
    private Button zipButton;
    private int copied;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The game can't be restarted in the same process; go back to it
        if (CarmageddonActivity.running) {
            startActivity(new Intent(this, CarmageddonActivity.class));
            finish();
            return;
        }
        root = getExternalFilesDir(null);
        migrateSingleGameLayout();
        buildUi();
        refreshGames();
    }

    static File gameDir(File root, String id) {
        return new File(root, id);
    }

    private boolean hasBundledData() {
        try {
            getAssets().open("game/filelist.txt").close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Bundled data is always the base game (built from -PgameDir). */
    private boolean isInstalled(String id) {
        return new File(gameDir(root, id), "DATA/GENERAL.TXT").exists()
            || (id.equals("carmageddon") && hasBundledData());
    }

    /**
     * Earlier versions kept one game directly in the files directory. Move it
     * (saves included) into its game folder. Renames on the same filesystem,
     * so this is instant.
     */
    private void migrateSingleGameLayout() {
        if (root == null || !new File(root, "DATA").isDirectory()) {
            return;
        }
        File dest = gameDir(root, isSplatPack(new File(root, "DATA")) ? "splatpack" : "carmageddon");
        if (dest.exists()) {
            return;
        }
        dest.mkdirs();
        for (String name : new String[] { "DATA", "MUSIC", "filelist.txt" }) {
            File from = new File(root, name);
            if (from.exists()) {
                from.renameTo(new File(dest, name));
            }
        }
    }

    private static boolean isSplatPack(File dataDir) {
        return new File(dataDir, "RACES/CASTLE.TXT").exists();
    }

    private void play(String id) {
        Intent intent = new Intent(this, CarmageddonActivity.class);
        intent.putExtra(EXTRA_GAME, id);
        startActivity(intent);
        finish();
    }

    // ---- UI ----

    private void buildUi() {
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(pad, pad, pad, pad);
        layout.setBackgroundColor(0xFF000000);

        TextView title = new TextView(this);
        title.setText("Carmageddon");
        title.setTextColor(0xFFFF3020);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        gameList = new LinearLayout(this);
        gameList.setOrientation(LinearLayout.HORIZONTAL);
        gameList.setGravity(Gravity.CENTER);
        gameList.setPadding(0, pad, 0, pad);
        layout.addView(gameList);

        TextView info = new TextView(this);
        info.setText("To add a game, copy your Carmageddon or Splat Pack folder (the one containing DATA, "
            + "and MUSIC if you have it) to this phone, or a ZIP of it, and choose it below. "
            + "The game is recognised automatically. You can delete your copy afterwards.");
        info.setTextColor(0xFFBBBBBB);
        info.setTextSize(14);
        info.setGravity(Gravity.CENTER);
        layout.addView(info);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, pad / 2, 0, 0);
        folderButton = new Button(this);
        folderButton.setText("Add from folder");
        folderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
            }
        });
        buttons.addView(folderButton);
        zipButton = new Button(this);
        zipButton.setText("Add from ZIP");
        zipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "application/zip", "application/x-zip-compressed" });
                startActivityForResult(intent, PICK_ZIP);
            }
        });
        buttons.addView(zipButton);
        layout.addView(buttons);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        layout.addView(progress);

        status = new TextView(this);
        status.setTextColor(0xFFDDDDDD);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);

        setContentView(layout);
    }

    /** One Play button per game; greyed out until its files are added. */
    private void refreshGames() {
        gameList.removeAllViews();
        int margin = Math.round(8 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < GAME_IDS.length; i++) {
            final String id = GAME_IDS[i];
            boolean installed = isInstalled(id);
            Button play = new Button(this);
            play.setText(installed ? "Play " + GAME_NAMES[i] : GAME_NAMES[i] + "\n(not added)");
            play.setTextSize(18);
            play.setEnabled(installed);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    play(id);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(margin, 0, margin, 0);
            gameList.addView(play, lp);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final boolean zip = requestCode == PICK_ZIP;
        setBusy(true, "Copying game files...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    copied = 0;
                    message = zip ? importZip(uri) : importFolder(uri);
                } catch (IOException | RuntimeException e) {
                    message = "Copy failed: " + e.getMessage();
                }
                final String result = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false, result);
                        refreshGames();
                    }
                });
            }
        }).start();
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        folderButton.setEnabled(!busy);
        zipButton.setEnabled(!busy);
        for (int i = 0; i < gameList.getChildCount(); i++) {
            gameList.getChildAt(i).setEnabled(!busy && isInstalled(GAME_IDS[i]));
        }
        status.setText(message);
    }

    private void showProgress() {
        if (copied % 50 == 0) {
            final int count = copied;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    status.setText("Copying game files... " + count);
                }
            });
        }
    }

    private static String addedMessage(boolean splat) {
        return (splat ? "Splat Pack" : "Carmageddon") + " added.";
    }

    // ---- Folder import (Storage Access Framework) ----

    private String importFolder(Uri tree) throws IOException {
        ContentResolver resolver = getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        // Accept the game folder itself, its parent, or the DATA folder
        String gameId = findGameFolder(resolver, tree, rootId, 2);
        if (gameId == null) {
            return "Couldn't find a DATA folder with GENERAL.TXT in the folder you chose.";
        }
        String[] dataDir = findChild(resolver, tree, gameId, "DATA");
        String[] races = findChild(resolver, tree, dataDir[0], "RACES");
        boolean splat = races != null && findChild(resolver, tree, races[0], "CASTLE.TXT") != null;
        File dest = gameDir(root, splat ? "splatpack" : "carmageddon");

        for (String[] child : listChildren(resolver, tree, gameId)) {
            String top = topLevelName(child[1]);
            if (top != null && DocumentsContract.Document.MIME_TYPE_DIR.equals(child[2])) {
                copyTree(resolver, tree, child[0], new File(dest, top), top.equals("DATA"), dest);
            }
        }
        return addedMessage(splat);
    }

    /** Returns the document id of the folder that contains DATA/GENERAL.TXT. */
    private String findGameFolder(ContentResolver resolver, Uri tree, String id, int depth) {
        if (findChild(resolver, tree, id, "GENERAL.TXT") != null) {
            // Picked DATA itself: the game folder is its parent, which we
            // can't reach, so treat this folder as DATA
            return "DATA:" + id;
        }
        String[] data = findChild(resolver, tree, id, "DATA");
        if (data != null && findChild(resolver, tree, data[0], "GENERAL.TXT") != null) {
            return id;
        }
        if (depth > 0) {
            for (String[] child : listChildren(resolver, tree, id)) {
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(child[2])) {
                    String found = findGameFolder(resolver, tree, child[0], depth - 1);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private String[] findChild(ContentResolver resolver, Uri tree, String id, String name) {
        for (String[] child : listChildren(resolver, tree, id)) {
            if (child[1].equalsIgnoreCase(name)) {
                return child;
            }
        }
        return null;
    }

    /** Children as {documentId, name, mimeType}. */
    private List<String[]> listChildren(ContentResolver resolver, Uri tree, String id) {
        List<String[]> children = new ArrayList<>();
        if (id.startsWith("DATA:")) {
            // Picked DATA directly: present it as a game folder containing DATA
            children.add(new String[] { id.substring(5), "DATA", DocumentsContract.Document.MIME_TYPE_DIR });
            return children;
        }
        Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        Cursor c = resolver.query(uri, new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE }, null, null, null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    children.add(new String[] { c.getString(0), c.getString(1), c.getString(2) });
                }
            } finally {
                c.close();
            }
        }
        return children;
    }

    private void copyTree(ContentResolver resolver, Uri tree, String id, File dest, boolean upperCase, File game) throws IOException {
        if (!dest.isDirectory() && !dest.mkdirs()) {
            throw new IOException("can't create " + dest);
        }
        for (String[] child : listChildren(resolver, tree, id)) {
            File target = new File(dest, upperCase ? child[1].toUpperCase(Locale.ROOT) : child[1]);
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(child[2])) {
                copyTree(resolver, tree, child[0], target, upperCase, game);
            } else if (!isPlayerFile(game, target)) {
                InputStream in = resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, child[0]));
                try {
                    writeFile(in, target);
                } finally {
                    in.close();
                }
            }
        }
    }

    // ---- ZIP import ----

    private String importZip(Uri uri) throws IOException {
        // Find where DATA sits inside the zip (it may be inside a top folder),
        // and whether it's the Splat Pack
        String prefix = null;
        boolean splat = false;
        List<String> names = new ArrayList<>();
        ZipInputStream zip = new ZipInputStream(getContentResolver().openInputStream(uri));
        try {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                names.add(e.getName().replace('\\', '/'));
            }
        } finally {
            zip.close();
        }
        for (String name : names) {
            if (name.toUpperCase(Locale.ROOT).endsWith("DATA/GENERAL.TXT")) {
                prefix = name.substring(0, name.length() - "DATA/GENERAL.TXT".length());
                break;
            }
        }
        if (prefix == null) {
            return "Couldn't find DATA/GENERAL.TXT in the ZIP.";
        }
        String castle = (prefix + "DATA/RACES/CASTLE.TXT").toUpperCase(Locale.ROOT);
        for (String name : names) {
            if (name.toUpperCase(Locale.ROOT).equals(castle)) {
                splat = true;
            }
        }
        File dest = gameDir(root, splat ? "splatpack" : "carmageddon");

        zip = new ZipInputStream(getContentResolver().openInputStream(uri));
        try {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                String name = e.getName().replace('\\', '/');
                if (e.isDirectory() || !name.startsWith(prefix) || name.contains("..")) {
                    continue;
                }
                String relative = name.substring(prefix.length());
                int slash = relative.indexOf('/');
                String top = slash > 0 ? topLevelName(relative.substring(0, slash)) : null;
                if (top == null) {
                    continue;
                }
                String rest = relative.substring(slash);
                File target = new File(dest, top + (top.equals("DATA") ? rest.toUpperCase(Locale.ROOT) : rest));
                if (isPlayerFile(dest, target)) {
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("can't create " + parent);
                }
                writeFile(zip, target);
            }
        } finally {
            zip.close();
        }
        return addedMessage(splat);
    }

    // ---- Helpers ----

    /**
     * DATA and MUSIC, spelled the way dethrace expects; null for anything else.
     * Everything under DATA is stored upper case like the original DOS files,
     * since some of dethrace's file checks are case sensitive.
     */
    private static String topLevelName(String name) {
        if (name.equalsIgnoreCase("DATA")) {
            return "DATA";
        }
        if (name.equalsIgnoreCase("MUSIC")) {
            return "MUSIC";
        }
        return null;
    }

    /** Saves and settings the game writes; keep the player's existing copies. */
    private static boolean isPlayerFile(File game, File target) {
        String path = target.getAbsolutePath().substring(game.getAbsolutePath().length()).toUpperCase(Locale.ROOT);
        return target.exists()
            && (path.startsWith("/DATA/SAVEGAME/") || path.equals("/DATA/OPTIONS.TXT") || path.startsWith("/DATA/KEYMAP"));
    }

    /** Writes the stream to target; the caller closes the stream. */
    private void writeFile(InputStream in, File target) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        OutputStream out = new FileOutputStream(target);
        try {
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } finally {
            out.close();
        }
        copied++;
        showProgress();
    }
}
