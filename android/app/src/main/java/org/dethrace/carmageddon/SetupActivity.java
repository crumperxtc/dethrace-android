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
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Launcher. Starts the game straight away when game data is available (bundled
 * in the APK, or imported earlier); otherwise asks the player to pick their
 * Carmageddon folder or a ZIP of it, and copies DATA and MUSIC into the app's
 * files directory, where android_main.c points dethrace.
 */
public class SetupActivity extends Activity {
    private static final int PICK_FOLDER = 1;
    private static final int PICK_ZIP = 2;

    private TextView status;
    private ProgressBar progress;
    private Button folderButton;
    private Button zipButton;
    private File root;
    private int copied;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = getExternalFilesDir(null);
        if (hasBundledData() || hasImportedData()) {
            startGame();
            return;
        }
        buildUi();
    }

    private boolean hasBundledData() {
        try {
            getAssets().open("game/filelist.txt").close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean hasImportedData() {
        return root != null && new File(root, "DATA/GENERAL.TXT").exists();
    }

    private void startGame() {
        startActivity(new Intent(this, CarmageddonActivity.class));
        finish();
    }

    private void buildUi() {
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
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

        TextView info = new TextView(this);
        info.setText("This app needs your own copy of Carmageddon's game files.\n\n"
            + "Copy your Carmageddon folder (the one containing DATA, and MUSIC if you have it) "
            + "to this phone, or a ZIP of it, then choose it below. The files are copied into "
            + "the app; you can delete your copy afterwards.");
        info.setTextColor(0xFFDDDDDD);
        info.setTextSize(16);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, pad / 2, 0, pad);
        layout.addView(info);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        folderButton = new Button(this);
        folderButton.setText("Choose folder");
        folderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
            }
        });
        buttons.addView(folderButton);
        zipButton = new Button(this);
        zipButton.setText("Choose ZIP");
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
        status.setPadding(0, pad / 2, 0, 0);
        layout.addView(status);

        setContentView(layout);
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
                String error;
                try {
                    copied = 0;
                    error = zip ? importZip(uri) : importFolder(uri);
                } catch (IOException | RuntimeException e) {
                    error = "Copy failed: " + e.getMessage();
                }
                final String result = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null && hasImportedData()) {
                            startGame();
                        } else {
                            setBusy(false, result != null ? result : "No DATA/GENERAL.TXT found in what you chose.");
                        }
                    }
                });
            }
        }).start();
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        folderButton.setEnabled(!busy);
        zipButton.setEnabled(!busy);
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

    // ---- Folder import (Storage Access Framework) ----

    private String importFolder(Uri tree) throws IOException {
        ContentResolver resolver = getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        // Accept the game folder itself, its parent, or the DATA folder
        String gameId = findGameFolder(resolver, tree, rootId, 2);
        if (gameId == null) {
            return "Couldn't find a DATA folder with GENERAL.TXT in the folder you chose.";
        }
        for (String[] child : listChildren(resolver, tree, gameId)) {
            String top = topLevelName(child[1]);
            if (top != null && DocumentsContract.Document.MIME_TYPE_DIR.equals(child[2])) {
                copyTree(resolver, tree, child[0], new File(root, top), top.equals("DATA"));
            }
        }
        return null;
    }

    /** Returns the document id of the folder that contains DATA/GENERAL.TXT. */
    private String findGameFolder(ContentResolver resolver, Uri tree, String id, int depth) {
        for (String[] child : listChildren(resolver, tree, id)) {
            if (child[1].equalsIgnoreCase("GENERAL.TXT")) {
                // Picked DATA itself: the game folder is its parent, which we
                // can't reach, so treat this folder as DATA
                return "DATA:" + id;
            }
        }
        for (String[] child : listChildren(resolver, tree, id)) {
            if (child[1].equalsIgnoreCase("DATA") && DocumentsContract.Document.MIME_TYPE_DIR.equals(child[2])) {
                for (String[] grandchild : listChildren(resolver, tree, child[0])) {
                    if (grandchild[1].equalsIgnoreCase("GENERAL.TXT")) {
                        return id;
                    }
                }
            }
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

    /** Children as {documentId, name, mimeType}. */
    private java.util.List<String[]> listChildren(ContentResolver resolver, Uri tree, String id) {
        java.util.List<String[]> children = new java.util.ArrayList<>();
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

    private void copyTree(ContentResolver resolver, Uri tree, String id, File dest, boolean upperCase) throws IOException {
        if (!dest.isDirectory() && !dest.mkdirs()) {
            throw new IOException("can't create " + dest);
        }
        for (String[] child : listChildren(resolver, tree, id)) {
            File target = new File(dest, upperCase ? child[1].toUpperCase(Locale.ROOT) : child[1]);
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(child[2])) {
                copyTree(resolver, tree, child[0], target, upperCase);
            } else if (!isPlayerFile(target)) {
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
        // Find where DATA sits inside the zip (it may be inside a top folder)
        String prefix = null;
        ZipInputStream zip = new ZipInputStream(getContentResolver().openInputStream(uri));
        try {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                String name = e.getName().replace('\\', '/');
                String upper = name.toUpperCase(Locale.ROOT);
                if (upper.endsWith("DATA/GENERAL.TXT")) {
                    prefix = name.substring(0, name.length() - "DATA/GENERAL.TXT".length());
                    break;
                }
            }
        } finally {
            zip.close();
        }
        if (prefix == null) {
            return "Couldn't find DATA/GENERAL.TXT in the ZIP.";
        }

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
                File target = new File(root, top + (top.equals("DATA") ? rest.toUpperCase(Locale.ROOT) : rest));
                if (isPlayerFile(target)) {
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
        return null;
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
    private boolean isPlayerFile(File target) {
        String path = target.getAbsolutePath().substring(root.getAbsolutePath().length()).toUpperCase(Locale.ROOT);
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
