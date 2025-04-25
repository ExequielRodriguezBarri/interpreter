import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class VSLangInterpreter {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java VSLangInterpreter <sourcefile>");
            return;
        }

        String source = new String(
            Files.readAllBytes(Paths.get(args[0])), 
            StandardCharsets.UTF_8
        );

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        Parser parser = new Parser(tokens);
        Program program = parser.parse();

        Evaluator evaluator = new Evaluator();
        evaluator.evaluate(program);
    }

    //--- Token Definitions ---
    enum TokenType {
        EOF, IDENT, INT, STRING,
        LET, PRINT, WHILE,
        LPAREN, RPAREN, LBRACE, RBRACE,
        SEMICOLON, COMMA,
        PLUS, MINUS, STAR, SLASH, EQ,
        LT, GT,
        LE, GE   // <= and >=
    }

    static class Token {
        TokenType type;
        String text;

        public Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    //--- Lexer: Source Code → Tokens ---
    static class Lexer {
        private final String input;
        private final int length;
        private int pos = 0;

        public Lexer(String input) {
            this.input = input;
            this.length = input.length();
        }

        private char peek() {
            return (pos < length) ? input.charAt(pos) : '\0';
        }

        private char next() {
            return (pos < length) ? input.charAt(pos++) : '\0';
        }

        public List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();

            while (true) {
                // Multi-char operators
                if (pos + 1 < length) {
                    if (input.charAt(pos) == '<' && input.charAt(pos + 1) == '=') {
                        tokens.add(new Token(TokenType.LE, "<="));
                        pos += 2;
                        continue;
                    }
                    if (input.charAt(pos) == '>' && input.charAt(pos + 1) == '=') {
                        tokens.add(new Token(TokenType.GE, ">="));
                        pos += 2;
                        continue;
                    }
                }

                char c = peek();
                if (c == '\0') {
                    tokens.add(new Token(TokenType.EOF, ""));
                    break;
                }
                if (Character.isWhitespace(c)) { pos++; continue; }

                if (Character.isLetter(c)) {
                    StringBuilder sb = new StringBuilder();
                    while (Character.isLetter(peek())) sb.append(next());
                    String word = sb.toString();
                    switch (word) {
                        case "let": tokens.add(new Token(TokenType.LET, word)); break;
                        case "print": tokens.add(new Token(TokenType.PRINT, word)); break;
                        case "while": tokens.add(new Token(TokenType.WHILE, word)); break;
                        default: tokens.add(new Token(TokenType.IDENT, word)); break;
                    }
                    continue;
                }

                if (Character.isDigit(c)) {
                    StringBuilder sb = new StringBuilder();
                    while (Character.isDigit(peek())) sb.append(next());
                    tokens.add(new Token(TokenType.INT, sb.toString()));
                    continue;
                }

                if (c == '"') {
                    next(); // skip '"'
                    StringBuilder sb = new StringBuilder();
                    while (peek() != '"' && peek() != '\0') sb.append(next());
                    next(); // skip closing '"'
                    tokens.add(new Token(TokenType.STRING, sb.toString()));
                    continue;
                }

                switch (c) {
                    case '(': tokens.add(new Token(TokenType.LPAREN, "(")); pos++; break;
                    case ')': tokens.add(new Token(TokenType.RPAREN, ")")); pos++; break;
                    case '{': tokens.add(new Token(TokenType.LBRACE, "{")); pos++; break;
                    case '}': tokens.add(new Token(TokenType.RBRACE, "}")); pos++; break;
                    case ';': tokens.add(new Token(TokenType.SEMICOLON, ";")); pos++; break;
                    case ',': tokens.add(new Token(TokenType.COMMA, ",")); pos++; break;
                    case '+': tokens.add(new Token(TokenType.PLUS, "+")); pos++; break;
                    case '-': tokens.add(new Token(TokenType.MINUS, "-")); pos++; break;
                    case '*': tokens.add(new Token(TokenType.STAR, "*")); pos++; break;
                    case '/': tokens.add(new Token(TokenType.SLASH, "/")); pos++; break;
                    case '=': tokens.add(new Token(TokenType.EQ, "=")); pos++; break;
                    case '<': tokens.add(new Token(TokenType.LT, "<")); pos++; break;
                    case '>': tokens.add(new Token(TokenType.GT, ">")); pos++; break;
                    default: throw new RuntimeException("Unexpected char: " + c);
                }
            }

            return tokens;
        }
    }

    //--- AST Node Definitions ---
    static class Program { List<Statement> statements; public Program(List<Statement> statements) { this.statements = statements; } }
    interface Statement {}
    interface Expression {}

    static class LetStmt implements Statement { String name; Expression expr; public LetStmt(String name, Expression expr) { this.name = name; this.expr = expr; } }
    static class PrintStmt implements Statement { Expression expr; public PrintStmt(Expression expr) { this.expr = expr; } }
    static class WhileStmt implements Statement { Expression condition; List<Statement> body; public WhileStmt(Expression condition, List<Statement> body) { this.condition = condition; this.body = body; } }
    static class ExprStmt implements Statement { Expression expr; public ExprStmt(Expression expr) { this.expr = expr; } }
    static class BinaryExpr implements Expression { Expression left; String op; Expression right; public BinaryExpr(Expression left, String op, Expression right) { this.left = left; this.op = op; this.right = right; } }
    static class NumberExpr implements Expression { int value; public NumberExpr(int value) { this.value = value; } }
    static class StringExpr implements Expression { String value; public StringExpr(String value) { this.value = value; } }
    static class VarExpr implements Expression { String name; public VarExpr(String name) { this.name = name; } }
    static class CallExpr implements Expression { String name; List<Expression> args; public CallExpr(String name, List<Expression> args) { this.name = name; this.args = args; } }

    //--- Parser: Tokens → AST ---
    static class Parser {
        private final List<Token> tokens; private int pos = 0;
        public Parser(List<Token> tokens) { this.tokens = tokens; }
        private Token peek() { return tokens.get(pos); }
        private Token next() { return tokens.get(pos++); }
        private boolean match(TokenType t) { if (peek().type == t) { pos++; return true; } return false; }
        private void expect(TokenType t) { if (peek().type != t) throw new RuntimeException("Expected " + t + " but got " + peek().type); pos++; }

        public Program parse() {
            List<Statement> stmts = new ArrayList<>();
            while (peek().type != TokenType.EOF) stmts.add(parseStatement());
            return new Program(stmts);
        }

        private Statement parseStatement() {
            if (match(TokenType.LET)) {
                String name = next().text; expect(TokenType.EQ); Expression expr = parseExpr(); expect(TokenType.SEMICOLON); return new LetStmt(name, expr);
            }
            if (match(TokenType.PRINT)) {
                expect(TokenType.LPAREN); Expression expr = parseExpr(); expect(TokenType.RPAREN); expect(TokenType.SEMICOLON); return new PrintStmt(expr);
            }
            if (match(TokenType.WHILE)) {
                expect(TokenType.LPAREN); Expression cond = parseExpr(); expect(TokenType.RPAREN); expect(TokenType.LBRACE);
                List<Statement> body = new ArrayList<>(); while (!match(TokenType.RBRACE)) body.add(parseStatement()); return new WhileStmt(cond, body);
            }
            if (peek().type == TokenType.IDENT && tokens.get(pos+1).type == TokenType.EQ) {
                String name = next().text; next(); Expression expr = parseExpr(); expect(TokenType.SEMICOLON); return new LetStmt(name, expr);
            }
            Expression expr = parseExpr(); expect(TokenType.SEMICOLON); return new ExprStmt(expr);
        }

        private Expression parseExpr() {
            Expression expr = parseTerm();
            while (peek().type == TokenType.PLUS || peek().type == TokenType.MINUS
                || peek().type == TokenType.LT   || peek().type == TokenType.GT
                || peek().type == TokenType.LE   || peek().type == TokenType.GE) {
                String op = next().text; Expression right = parseTerm(); expr = new BinaryExpr(expr, op, right);
            }
            return expr;
        }

        private Expression parseTerm() {
            Expression expr = parseFactor();
            while (peek().type == TokenType.STAR || peek().type == TokenType.SLASH) {
                String op = next().text; Expression right = parseFactor(); expr = new BinaryExpr(expr, op, right);
            }
            return expr;
        }

        private Expression parseFactor() {
            Token tok = peek();
            if (tok.type == TokenType.INT) { next(); return new NumberExpr(Integer.parseInt(tok.text)); }
            if (tok.type == TokenType.STRING) { next(); return new StringExpr(tok.text); }
            if (tok.type == TokenType.IDENT) {
                if (tokens.get(pos+1).type == TokenType.LPAREN) {
                    String name = next().text; next(); List<Expression> args = new ArrayList<>();
                    if (peek().type != TokenType.RPAREN) do { args.add(parseExpr()); } while (match(TokenType.COMMA)); expect(TokenType.RPAREN);
                    return new CallExpr(name, args);
                } else { next(); return new VarExpr(tok.text); }
            }
            if (match(TokenType.LPAREN)) { Expression expr = parseExpr(); expect(TokenType.RPAREN); return expr; }
            throw new RuntimeException("Unexpected token: " + tok.type);
        }
    }

    //--- Evaluator: AST → Execution ---
    static class Evaluator {
        private final Map<String, Object> env = new HashMap<>();
        private final Scanner scanner = new Scanner(System.in);

        public void evaluate(Program program) {
            for (Statement stmt : program.statements) evalStmt(stmt);
        }

        private void evalStmt(Statement stmt) {
            if (stmt instanceof LetStmt) {
                LetStmt let = (LetStmt) stmt; Object val = evalExpr(let.expr); env.put(let.name, val);
            } else if (stmt instanceof PrintStmt) {
                System.out.println(evalExpr(((PrintStmt) stmt).expr));
            } else if (stmt instanceof WhileStmt) {
                WhileStmt ws = (WhileStmt) stmt; while (truthy(evalExpr(ws.condition))) for (Statement s : ws.body) evalStmt(s);
            } else if (stmt instanceof ExprStmt) {
                evalExpr(((ExprStmt) stmt).expr);
            }
        }

        private Object evalExpr(Expression expr) {
            if (expr instanceof NumberExpr) return ((NumberExpr) expr).value;
            if (expr instanceof StringExpr) return ((StringExpr) expr).value;
            if (expr instanceof VarExpr) {
                String name = ((VarExpr) expr).name;
                if (!env.containsKey(name)) throw new RuntimeException("Undefined variable: " + name);
                return env.get(name);
            }
            if (expr instanceof BinaryExpr) {
                BinaryExpr be = (BinaryExpr) expr; Object l = evalExpr(be.left), r = evalExpr(be.right);
                if (l instanceof Integer && r instanceof Integer) {
                    int a = (Integer) l, b = (Integer) r;
                    switch (be.op) {
                        case "<=" : return a <= b ? 1 : 0;
                        case ">=" : return a >= b ? 1 : 0;
                        case "<"  : return a <  b ? 1 : 0;
                        case ">"  : return a >  b ? 1 : 0;
                        case "+"  : return a + b;
                        case "-"  : return a - b;
                        case "*"  : return a * b;
                        case "/"  : return a / b;
                    }
                }
                if ("+".equals(be.op)) return l.toString() + r.toString();
                throw new RuntimeException("Type error in binary op: " + be.op);
            }
            if (expr instanceof CallExpr) {
                CallExpr ce = (CallExpr) expr;
                switch (ce.name) {
                    case "read":
                        String line = scanner.nextLine();
                        return line.matches("\\d+") ? Integer.parseInt(line) : line;
                    case "len": {
                        Object o = evalExpr(ce.args.get(0));
                        if (o instanceof String) return ((String) o).length();
                        throw new RuntimeException("len() expects a string");
                    }
                    case "substr":
                    case "substring": {
                        String s = (String) evalExpr(ce.args.get(0));
                        int st = (Integer) evalExpr(ce.args.get(1));
                        int cnt = (Integer) evalExpr(ce.args.get(2));
                        return s.substring(st, Math.min(s.length(), st + cnt));
                    }
                    default:
                        throw new RuntimeException("Unknown function: " + ce.name);
                }
            }
            throw new RuntimeException("Unknown expression type: " + expr.getClass());
        }

        private boolean truthy(Object o) {
            if (o instanceof Integer) return ((Integer) o) != 0;
            if (o instanceof String) return !((String) o).isEmpty();
            return o != null;
        }
    }
}
