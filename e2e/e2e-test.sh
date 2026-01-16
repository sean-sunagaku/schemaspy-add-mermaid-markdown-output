#!/bin/bash
# =============================================================================
# E2E Test Script for SchemaSpy Mermaid Markdown Output
# =============================================================================
# Usage:
#   ./e2e-test.sh           # Run all tests
#   ./e2e-test.sh mysql     # Run MySQL test only
#   ./e2e-test.sh postgres  # Run PostgreSQL test only
#   ./e2e-test.sh sqlite    # Run SQLite test only
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track test results
TESTS_PASSED=0
TESTS_FAILED=0

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

# Check if required files exist
check_output_files() {
    local output_dir="$1"
    local markdown_dir="$output_dir/markdown"
    local all_exist=true

    # Check markdown directory
    if [ ! -d "$markdown_dir" ]; then
        log_fail "Markdown directory not found: $markdown_dir"
        return 1
    fi

    # Check required files
    for file in "overview.md" "er-diagram.md" "tables.md"; do
        if [ ! -f "$markdown_dir/$file" ]; then
            log_fail "Missing file: $markdown_dir/$file"
            all_exist=false
        fi
    done

    if [ "$all_exist" = true ]; then
        log_success "All markdown files exist"
        return 0
    else
        return 1
    fi
}

# Check if ER diagram contains expected content
check_er_diagram_content() {
    local output_dir="$1"
    local er_file="$output_dir/markdown/er-diagram.md"
    local all_ok=true

    # Check for Mermaid code block
    if ! grep -q '```mermaid' "$er_file"; then
        log_fail "ER diagram missing mermaid code block"
        all_ok=false
    fi

    # Check for erDiagram declaration
    if ! grep -q 'erDiagram' "$er_file"; then
        log_fail "ER diagram missing erDiagram declaration"
        all_ok=false
    fi

    # Check for tables (users, posts, comments, tags, post_tags)
    for table in "users" "posts" "comments" "tags" "post_tags"; do
        if ! grep -q "$table {" "$er_file"; then
            log_fail "ER diagram missing table: $table"
            all_ok=false
        fi
    done

    # Check for relationships
    if ! grep -q '||--o{' "$er_file"; then
        log_fail "ER diagram missing relationships"
        all_ok=false
    fi

    if [ "$all_ok" = true ]; then
        log_success "ER diagram content is valid"
        return 0
    else
        return 1
    fi
}

# Check tables.md content
check_tables_content() {
    local output_dir="$1"
    local tables_file="$output_dir/markdown/tables.md"
    local all_ok=true

    # Check for table headers
    if ! grep -q '# Table Details' "$tables_file"; then
        log_fail "tables.md missing header"
        all_ok=false
    fi

    # Check for column table header
    if ! grep -q '| Column | Type |' "$tables_file"; then
        log_fail "tables.md missing column table"
        all_ok=false
    fi

    # Check for Foreign Key section
    if ! grep -q '## Foreign Key Constraints' "$tables_file"; then
        log_fail "tables.md missing Foreign Key Constraints section"
        all_ok=false
    fi

    if [ "$all_ok" = true ]; then
        log_success "tables.md content is valid"
        return 0
    else
        return 1
    fi
}

# Run test for a specific database
run_test() {
    local db_type="$1"
    local test_dir="$SCRIPT_DIR/$db_type"
    local output_dir="$test_dir/output"

    echo ""
    echo "=============================================="
    echo " Testing: $db_type"
    echo "=============================================="

    # Check if test directory exists
    if [ ! -d "$test_dir" ]; then
        log_error "Test directory not found: $test_dir"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    # Clean up previous output
    log_info "Cleaning up previous output..."
    rm -rf "$output_dir"

    # Start containers
    log_info "Starting $db_type containers..."
    cd "$test_dir"

    if ! docker compose up --abort-on-container-exit 2>&1 | grep -E '(INFO|ERROR|WARN|exited)'; then
        log_error "Docker compose failed"
        docker compose down -v 2>/dev/null || true
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    # Clean up containers
    docker compose down -v 2>/dev/null || true

    echo ""
    log_info "Verifying output..."

    # Run checks
    local test_passed=true

    if ! check_output_files "$output_dir"; then
        test_passed=false
    fi

    if ! check_er_diagram_content "$output_dir"; then
        test_passed=false
    fi

    if ! check_tables_content "$output_dir"; then
        test_passed=false
    fi

    if [ "$test_passed" = true ]; then
        log_success "$db_type test PASSED"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        log_fail "$db_type test FAILED"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Main
main() {
    echo "=============================================="
    echo " SchemaSpy Mermaid Markdown E2E Tests"
    echo "=============================================="

    local target="${1:-all}"

    case "$target" in
        mysql)
            run_test "mysql"
            ;;
        postgres)
            run_test "postgres"
            ;;
        sqlite)
            run_test "sqlite"
            ;;
        all)
            run_test "sqlite" || true
            run_test "mysql" || true
            run_test "postgres" || true
            ;;
        *)
            echo "Usage: $0 [mysql|postgres|sqlite|all]"
            exit 1
            ;;
    esac

    echo ""
    echo "=============================================="
    echo " Test Summary"
    echo "=============================================="
    echo -e "  Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed: ${RED}$TESTS_FAILED${NC}"
    echo "=============================================="

    if [ $TESTS_FAILED -gt 0 ]; then
        exit 1
    fi
}

main "$@"
