# ============================================================================
# 聚山智慧停车 SaaS 平台 — Monorepo 构建编排
#
# 项目结构：
#   ./                   平台侧（Java 21 + Spring Boot 3.5.16）
#   device-access/       设备侧（Java 17 + Spring Boot 3.3.7）
#   booth-web/           统一 Web 前端（Vue 3，运营端+岗亭端）
#
# 注意：平台侧和 Device Access 是两套独立的 Maven 项目，
#      需要不同的 JDK 版本，各自独立编译。
# ============================================================================

.PHONY: help \
        build-platform build-device-access build-all \
        test-platform test-device-access test-all \
        build-booth build-frontend \
        clean-platform clean-device-access clean clean-all \
        package-platform package-device-access

# 默认目标
.DEFAULT_GOAL := help

# Maven 命令（平台侧用 wrapper，设备侧用系统 mvn）
MVNW    := ./mvnw
MVN     := mvn

# ============================================================================
# 帮助
# ============================================================================
help:
	@echo "聚山智慧停车 Monorepo 构建命令"
	@echo ""
	@echo "  后端构建:"
	@echo "    make build-platform        编译平台侧（Java 21）"
	@echo "    make build-device-access   编译设备侧（Java 17）"
	@echo "    make build-all             编译两侧"
	@echo ""
	@echo "  测试:"
	@echo "    make test-platform         测试平台侧"
	@echo "    make test-device-access    测试设备侧"
	@echo "    make test-all              测试两侧"
	@echo ""
	@echo "  前端构建:"
	@echo "    make build-booth           构建统一 Web 前端（运营端+岗亭端合一）"
	@echo "    make build-frontend        构建统一 Web 前端（同 build-booth）"
	@echo ""
	@echo "  打包:"
	@echo "    make package-platform      打包平台侧"
	@echo "    make package-device-access 打包设备侧"
	@echo ""
	@echo "  清理:"
	@echo "    make clean                 清理所有编译产物"

# ============================================================================
# 平台侧（Java 21, Spring Boot 3.5.16）
# ============================================================================
build-platform:
	@echo "=== 编译平台侧 (Java 21) ==="
	$(MVNW) clean compile -pl parking-system -am

test-platform:
	@echo "=== 测试平台侧 ==="
	$(MVNW) test -pl parking-boot -am

package-platform:
	@echo "=== 打包平台侧 ==="
	$(MVNW) clean package -pl parking-boot -am -DskipTests

# ============================================================================
# 设备侧（Java 17, Spring Boot 3.3.7）
# ============================================================================
build-device-access:
	@echo "=== 编译设备侧 (Java 17) ==="
	cd device-access && $(MVN) clean compile

test-device-access:
	@echo "=== 测试设备侧 ==="
	cd device-access && $(MVN) test

package-device-access:
	@echo "=== 打包设备侧 ==="
	cd device-access && $(MVN) clean package -DskipTests

# ============================================================================
# 全部后端
# ============================================================================
build-all: build-platform build-device-access

test-all: test-platform test-device-access

# ============================================================================
# 前端
# ============================================================================
build-booth:
	@echo "=== 构建统一 Web 前端 ==="
	cd booth-web && pnpm install && pnpm build

build-frontend: build-booth

# ============================================================================
# 清理
# ============================================================================
clean-platform:
	$(MVNW) clean

clean-device-access:
	cd device-access && $(MVN) clean

clean: clean-platform clean-device-access
	@echo "=== 清理完成 ==="
