package httputils

import (
    "fmt"
    "net/http"
    "strings"
    "mime"
    "encoding/json"
    "golang.org/x/net/context"
    "github.com/Sirupsen/logrus"
)

// APIVersionKey is the client's requested API version.
const APIVersionKey = "api-version"

// APIFunc is an adapter to allow the use of ordinary functions as API endpoints.
// Any function that has the appropriate signature can be registered as a API endpoint.
type APIFunc func(ctx context.Context, w http.ResponseWriter, r *http.Request, vars map[string]string) error

// CheckForJSON makes sure that the request's Content-Type is application/json.
func CheckForJSON(r *http.Request) error {
    ct := r.Header.Get("Content-Type")

    // No Content-Type header is ok as long as there's no body
    if ct == "" {
        if r.Body == nil || r.ContentLength == 0 {
            return nil
        }
    }

    // Otherwise it better be json
    if MatchesContentType(ct, "application/json") {
        return nil
    }
    return fmt.Errorf("Content-Type specified (%s) must be 'application/json'", ct)
}

// WriteJSON writes the value to the http response stream as json with standard json encoding.
func WriteJSON(w http.ResponseWriter, code int, v interface{}) error {
    w.Header().Set("Content-Type", "application/json")
    w.WriteHeader(code)
    return json.NewEncoder(w).Encode(v)
}

// ParseForm ensures the request form is parsed even with invalid content types.
// If we don't do this, POST method without Content-type (even with empty body) will fail.
func ParseForm(r *http.Request) error {
    if r == nil {
        return nil
    }
    if err := r.ParseForm(); err != nil && !strings.HasPrefix(err.Error(), "mime:") {
        return err
    }
    return nil
}

// MatchesContentType validates the content type against the expected one
func MatchesContentType(contentType, expectedType string) bool {
    mimeType, _, err := mime.ParseMediaType(contentType)
    if err != nil {
        logrus.WithError(err).Errorf("Error parsing media type: %s", contentType)
    }
    return err == nil && mimeType == expectedType
}