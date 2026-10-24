package book_log.demo.security;

// access와 refresh를 같은 키로 서명하므로, 토큰 안의 type claim으로 용도를 구분한다
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }
}
