// Android entry point: SDLActivity loads libmain.so and calls SDL_main.
// Unpacks the game data bundled in the APK (assets/game) to the app's
// external files directory, points dethrace at it and forwards
// stdout/stderr to logcat.

#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "SDL.h"

extern int dethrace_main(int argc, char* argv[]);
extern void Android_AddCameraDrag(float pYaw_degrees, float pZoom);
extern void Android_ResetCamera(void);

JNIEXPORT void JNICALL Java_org_dethrace_carmageddon_CarmageddonActivity_nativeCameraDrag(JNIEnv* env, jclass cls, jfloat yaw_degrees, jfloat zoom) {
    Android_AddCameraDrag(yaw_degrees, zoom);
}

JNIEXPORT void JNICALL Java_org_dethrace_carmageddon_CarmageddonActivity_nativeCameraReset(JNIEnv* env, jclass cls) {
    Android_ResetCamera();
}

static int log_pipe[2];

static void* log_thread(void* arg) {
    char line[1024];
    FILE* in = fdopen(log_pipe[0], "r");

    (void)arg;
    while (fgets(line, sizeof(line), in) != NULL) {
        __android_log_write(ANDROID_LOG_INFO, "dethrace", line);
    }
    return NULL;
}

static void redirect_output_to_logcat(void) {
    pthread_t thread;

    setvbuf(stdout, NULL, _IOLBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    if (pipe(log_pipe) != 0) {
        return;
    }
    dup2(log_pipe[1], STDOUT_FILENO);
    dup2(log_pipe[1], STDERR_FILENO);
    if (pthread_create(&thread, NULL, log_thread, NULL) == 0) {
        pthread_detach(thread);
    }
}

// Reads a whole asset or file into a NUL-terminated buffer; NULL if missing.
static char* read_all(const char* path) {
    SDL_RWops* rw = SDL_RWFromFile(path, "rb");
    Sint64 size;
    char* buf;

    if (rw == NULL) {
        return NULL;
    }
    size = SDL_RWsize(rw);
    buf = malloc(size + 1);
    if (buf != NULL) {
        buf[SDL_RWread(rw, buf, 1, size)] = '\0';
    }
    SDL_RWclose(rw);
    return buf;
}

static void make_parent_dirs(char* path) {
    char* p;

    for (p = strchr(path + 1, '/'); p != NULL; p = strchr(p + 1, '/')) {
        *p = '\0';
        mkdir(path, 0755);
        *p = '/';
    }
}

// Files the game writes; an update must not clobber the player's copies.
static int is_player_file(const char* path) {
    return strncmp(path, "DATA/SAVEGAME/", 14) == 0
        || strcmp(path, "DATA/OPTIONS.TXT") == 0
        || strncmp(path, "DATA/KEYMAP", 11) == 0;
}

static int copy_asset(const char* asset_path, const char* dest_path) {
    static char buffer[256 * 1024];
    SDL_RWops* in = SDL_RWFromFile(asset_path, "rb");
    FILE* out;
    size_t n;
    int ok = 1;

    if (in == NULL) {
        return 0;
    }
    out = fopen(dest_path, "wb");
    if (out == NULL) {
        SDL_RWclose(in);
        return 0;
    }
    while ((n = SDL_RWread(in, buffer, 1, sizeof(buffer))) > 0) {
        if (fwrite(buffer, 1, n, out) != n) {
            ok = 0;
            break;
        }
    }
    SDL_RWclose(in);
    return fclose(out) == 0 && ok;
}

// Unpacks assets/game into root unless root already holds this APK's data.
// root/filelist.txt is written last, so an interrupted unpack is redone.
static int extract_game_data(const char* root) {
    char marker[1024];
    char asset_path[1024];
    char dest_path[1024];
    char* list;
    char* installed;
    char* line;
    char* tab;
    int count = 0;

    list = read_all("game/filelist.txt");
    if (list == NULL) {
        // APK built without game data; expect it to be copied over by hand
        return 1;
    }
    snprintf(marker, sizeof(marker), "%s/filelist.txt", root);
    installed = read_all(marker);
    if (installed != NULL && strcmp(installed, list) == 0) {
        free(installed);
        free(list);
        return 1;
    }
    free(installed);

    printf("Unpacking game data to %s\n", root);
    for (line = strtok(list, "\n"); line != NULL; line = strtok(NULL, "\n")) {
        tab = strchr(line, '\t');
        if (tab != NULL) {
            *tab = '\0';
        }
        snprintf(dest_path, sizeof(dest_path), "%s/%s", root, line);
        if (is_player_file(line) && access(dest_path, F_OK) == 0) {
            continue;
        }
        snprintf(asset_path, sizeof(asset_path), "game/%s", line);
        make_parent_dirs(dest_path);
        if (!copy_asset(asset_path, dest_path)) {
            printf("Failed to unpack %s: %s\n", line, strerror(errno));
            free(list);
            return 0;
        }
        count++;
    }
    free(list);

    if (!copy_asset("game/filelist.txt", marker)) {
        return 0;
    }
    printf("Unpacked %d files\n", count);
    return 1;
}

int SDL_main(int argc, char* argv[]) {
    const char* root;
    char message[1024];
    char widescreen_arg[32];
    // -hires: original 640x480 mode (DATA/64X48X8)
    char* args[] = { "dethrace", "--no-signal-handler", "-hires", widescreen_arg, NULL };
    SDL_DisplayMode mode;
    int width = 640;

    redirect_output_to_logcat();

    // /storage/emulated/0/Android/data/<package>/files
    root = SDL_AndroidGetExternalStoragePath();
    if (root == NULL) {
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "Carmageddon", SDL_GetError(), NULL);
        return 1;
    }
    setenv("DETHRACE_ROOT_DIR", root, 1);
    chdir(root);

    if (!extract_game_data(root)) {
        snprintf(message, sizeof(message),
            "Couldn't unpack the game data to:\n\n%s\n\nCheck there is about 350 MB of free space.",
            root);
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "Carmageddon", message, NULL);
        return 1;
    }

    if (access("DATA/GENERAL.TXT", F_OK) != 0) {
        snprintf(message, sizeof(message),
            "Game data not found.\n\nCopy the contents of your Carmageddon folder "
            "(CARMA.EXE, DATA, MUSIC) to:\n\n%s",
            root);
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "Carmageddon", message, NULL);
        return 1;
    }

    // Widescreen: CarmageddonActivity.getArguments() passes --widescreen=<width>
    // from the real display size; fall back to asking SDL
    if (argc > 1 && strncmp(argv[1], "--widescreen=", 13) == 0) {
        snprintf(widescreen_arg, sizeof(widescreen_arg), "%s", argv[1]);
    } else {
        if (SDL_Init(SDL_INIT_VIDEO) == 0 && SDL_GetDesktopDisplayMode(0, &mode) == 0 && SDL_min(mode.w, mode.h) > 0) {
            width = SDL_clamp(480 * SDL_max(mode.w, mode.h) / SDL_min(mode.w, mode.h), 640, 1280);
        }
        snprintf(widescreen_arg, sizeof(widescreen_arg), "--widescreen=%d", width);
    }
    printf("Using %s\n", widescreen_arg);

    return dethrace_main(SDL_arraysize(args) - 1, args);
}
