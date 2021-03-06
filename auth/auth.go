package auth

import (
	"crypto/rand"
	"net/http"
	"time"

	"github.com/Sirupsen/logrus"
	"github.com/dgrijalva/jwt-go"
	"github.com/dgrijalva/jwt-go/request"

	"github.com/cloudway/platform/auth/userdb"
)

const _TOKEN_EXPIRE_TIME = time.Hour * 24 * 30 // 30 days

// The authenticator authenticate user via http protocol.
type Authenticator struct {
	userdb *userdb.UserDatabase
	secret []byte
}

func NewAuthenticator(userdb *userdb.UserDatabase) (*Authenticator, error) {
	secret, err := userdb.GetSecret("jwt", func() []byte {
		secret := make([]byte, 64)
		rand.Read(secret)
		return secret
	})
	if err != nil {
		return nil, err
	}

	return &Authenticator{userdb, secret}, nil
}

type customClaims struct {
	*jwt.StandardClaims
	Namespace string `json:"ns"`
}

// Authenticate user with name and password. Returns the User object
// and a token.
func (auth *Authenticator) Authenticate(username, password string) (*userdb.BasicUser, string, error) {
	// Authenticate user by user database
	user, err := auth.userdb.Authenticate(username, password)
	if err != nil {
		return nil, "", err
	}

	// Create a new token object, specifying singing method and the claims
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, &customClaims{
		&jwt.StandardClaims{
			ExpiresAt: time.Now().Add(_TOKEN_EXPIRE_TIME).Unix(),
			Subject:   user.Name,
		},
		user.Namespace,
	})

	// Sign and get the complete encoded token as a string using the secret
	logrus.Debugf("Authenticated user: %v", user.Name)
	tokenString, err := token.SignedString(auth.secret)
	return user, tokenString, err
}

// Verify the current http request is authorized.
func (auth *Authenticator) Verify(r *http.Request) (*userdb.BasicUser, error) {
	var claims customClaims

	// Get token from request
	_, err := request.ParseFromRequestWithClaims(r, request.AuthorizationHeaderExtractor, &claims,
		func(token *jwt.Token) (interface{}, error) {
			return auth.secret, nil
		})

	// If the token is missing or invalid, return error
	if err != nil {
		return nil, err
	}

	return &userdb.BasicUser{Name: claims.Subject, Namespace: claims.Namespace}, nil
}
