package hub

import (
    "os"
    "fmt"
    "strings"
    "path/filepath"
    "github.com/cloudway/platform/container/conf"
    "github.com/cloudway/platform/pkg/archive"
    "github.com/cloudway/platform/pkg/manifest"
    "github.com/cloudway/platform/pkg/files"
)

type PluginHub struct {
    installDir string
}

func New() (*PluginHub, error) {
    dir := conf.GetOrDefault("hub.dir", "/var/lib/cloudway/plugins")
    if err := os.MkdirAll(dir, 0755); err != nil {
        return nil, err
    }
    return &PluginHub{dir}, nil
}

func (hub *PluginHub) GetPluginPath(tag string) (string, error) {
    var namespace, name, version string

    parts := strings.SplitN(tag, "/", 2)
    if len(parts) == 2 {
        namespace = parts[0]
        tag = parts[1]
    }

    parts = strings.SplitN(tag, ":", 2)
    if len(parts) == 2 {
        name, version = parts[0], parts[1]
    } else {
        name, version = parts[0], ""
    }

    base := hub.getBaseDir(namespace, name)
    versions, err := getAllVersions(base)
    if err != nil {
        if os.IsNotExist(err) {
            err = fmt.Errorf("Plugin not found: %s", name)
        }
        return "", err
    }

    actualVersion := matchVersion(versions, version)
    if actualVersion == "" {
        return "", fmt.Errorf("Version not found: %s", version)
    }
    return filepath.Join(base, actualVersion), nil
}

func (hub *PluginHub) GetPluginInfo(tag string) (*manifest.Plugin, error) {
    path, err := hub.GetPluginPath(tag)
    if err != nil {
        return nil, err
    }
    return archive.ReadManifest(path)
}

func (hub *PluginHub) InstallPlugin(namespace string, path string) (err error) {
    meta, err := archive.ReadManifest(path)
    if err != nil {
        return err
    }

    installDir := filepath.Join(hub.getBaseDir(namespace, meta.Name), meta.Version)
    if err = os.RemoveAll(installDir); err != nil {
        return err
    }
    if err = os.MkdirAll(installDir, 0755); err != nil {
        return err
    }

    if fi, _ := os.Stat(path); fi.IsDir() {
        return files.CopyFiles(path, installDir)
    } else {
        return files.ExtractFiles(path, installDir)
    }
}

func (hub *PluginHub) getBaseDir(namespace, name string) string {
    if namespace == "" {
        return filepath.Join(hub.installDir, ".cloudway", name)
    } else {
        return filepath.Join(hub.installDir, namespace, name)
    }
}