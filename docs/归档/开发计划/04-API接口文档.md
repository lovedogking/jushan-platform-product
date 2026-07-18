# 4. 核心 API 接口文档

## 4.1 接口清单

| 序号 | 路径 | 方法 | 功能 | 幂等键 | 并发控制 |
|------|------|------|------|--------|----------|
| 1 | `/api/v1/parking-lots` | POST | 创建停车场 | 必填 | 租户内按名称去重 + DB 唯一索引 |
| 2 | `/api/v1/parking-lots` | GET | 查询停车场列表 | 不需要 | tenant_id 过滤 + 软删除过滤 |
| 3 | `/api/v1/parking-lots/{lotId}` | GET | 获取停车场详情 | 不需要 | tenant_id 过滤 |
| 4 | `/api/v1/parking-lots/{lotId}/zones` | POST | 创建区域 | 必填 | 车位总数校验 + 乐观锁 |
| 5 | `/api/v1/parking-lots/{lotId}/zones` | GET | 查询区域列表 | 不需要 | tenant_id 过滤 |
| 6 | `/api/v1/parking-lots/{lotId}/lanes` | POST | 创建通道 | 必填 | 通道编号唯一 + 设备绑定校验 |
| 7 | `/api/v1/parking-lots/{lotId}/lanes` | GET | 查询通道列表 | 不需要 | tenant_id 过滤 |
| 8 | `/api/v1/parking-lots/{lotId}/vehicles` | POST | 车辆登记 | 必填 | 车牌去重 + 车位占用乐观锁 |
| 9 | `/api/v1/parking-lots/{lotId}/fee-rules` | POST | 创建收费规则 | 必填 | 规则冲突校验 + 乐观锁 |
| 10 | `/api/v1/fee/calculate` | POST | 停车费用计算 | 不需要 | 无写操作，只读计算 |
| 11 | `/api/v1/access/entry` | POST | 车辆入场 | 必填 | Redis 分布式锁（车牌 + 车场）+ 车位余量乐观锁 |
| 12 | `/api/v1/access/exit` | POST | 车辆出场 | 必填 | Redis 分布式锁（车牌 + 车场）+ 订单状态机 |
| 13 | `/api/v1/orders` | POST | 创建停车收费订单 | 必填 | 分布式锁（车牌 + 车场）+ 订单号幂等 |
| 14 | `/api/v1/orders/{orderId}/pay` | POST | 订单支付 | 必填 | 分布式锁（订单号）+ 支付渠道幂等 |
| 15 | `/api/v1/coupons/available` | GET | 查询可用优惠券 | 不需要 | 只读查询，库存预扣在核销时加锁 |
| 16 | `/api/v1/points/balance` | GET | 查询积分余额 | 不需要 | 只读查询，扣减时加锁 |
| 17 | `/api/v1/orders/{orderId}/discounts` | POST | 使用优惠券/积分抵扣 | 必填 | Redis 分布式锁 + 优惠券/积分乐观锁 |
| 18 | `/api/v1/visitors/appointments` | POST | 访客预约 | 必填 | 分布式锁（车牌 + 预约时段）+ 余位校验 |
| 19 | `/api/v1/parking-lots/{lotId}/blacklist` | POST | 添加黑名单 | 必填 | 分布式锁（车牌 + 车场）+ 乐观锁 |
| 20 | `/api/v1/parking-lots/{lotId}/spaces/remaining` | GET | 余位查询 | 不需要 | Redis 缓存 + DB 兜底，读多写少 |

## 4.2 共享规范落地说明

1. **多租户隔离**：所有接口默认从 JWT/Sa-Token 解析 `tenant_id`；超管通过请求头 `X-Tenant-Id` 显式指定目标租户。后端所有查询必须追加 `tenant_id = ? AND deleted_at IS NULL`。
2. **软删除**：所有业务表含 `deleted_at DATETIME DEFAULT NULL`；删除操作更新该字段，物理删除仅由后台归档任务执行。
3. **金额**：接口传输入参以 **字符串型整数分** 为主（如 `"1500"`），内部使用 `java.math.BigDecimal` / `DECIMAL(18,2)`，禁止 `FLOAT/DOUBLE`。
4. **车牌**：入参车牌业务层转大写并按 GA36-2018 校验；数据库存储大写；查询时忽略大小写。
5. **操作日志**：所有写接口通过 AOP 记录 `operator_id`、`operator_ip`、`operation_time`、`before_value`/`after_value`（JSON）。
6. **并发控制**：车位余量、优惠券库存、积分扣减使用 **Redis 分布式锁（Redisson）+ 数据库乐观锁（version 字段）** 双重保护。
7. **时间**：请求/响应使用 ISO 8601（如 `2026-07-12T14:32:18+08:00`）；数据库使用 `DATETIME(3)`。
8. **预留接口**：设备指令下发、P云支付渠道直连等接口标注【预留】，当前返回 mock 或空实现，但字段定义完整。P云开放平台为当前唯一支付渠道。
9. **鉴权**：使用 Sa-Token/JWT；请求头 `Authorization: Bearer {token}`。
10. **幂等**：除明确只读接口外，所有 POST/PUT/DELETE 必须携带 `X-Idempotency-Key`（UUID），服务端 24 小时内同一键返回首次结果。

## 4.3 OpenAPI 3.0 定义

```yaml
openapi: 3.0.3
info:
  title: 飓山停车 SaaS 平台核心 API
  description: |
    覆盖租户/车场/区域/通道管理、车辆登记、收费计算、车辆入场/出场、订单创建与支付、优惠券/积分查询与使用、访客预约、黑名单、余位查询等核心能力。
    所有写接口需携带幂等键 X-Idempotency-Key；鉴权采用 Sa-Token/JWT（Authorization: Bearer {token}）。
  version: 1.0.0
  contact:
    name: 飓山智能
servers:
  - url: https://api.jushan-parking.com
    description: 生产环境
  - url: https://api-dev.jushan-parking.com
    description: 测试环境

security:
  - BearerAuth: []

paths:
  /api/v1/parking-lots:
    post:
      summary: 创建停车场
      description: 租户管理员创建停车场档案，创建成功后自动生成默认区域与默认收费规则。
      operationId: createParkingLot
      tags:
        - 车场管理
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ParkingLotCreateReq'
            example:
              name: XX商场地下停车场
              companyId: 10001
              groupId: 1001
              regionType: MALL
              province: 北京市
              city: 北京市
              district: 朝阳区
              address: 建国路88号
              longitude: "116.481488"
              latitude: "39.913385"
              contactName: 张经理
              contactPhone: "13800138000"
              status: OPEN
              businessHoursStart: "00:00:00"
              businessHoursEnd: "24:00:00"
      responses:
        '200':
          $ref: '#/components/responses/ParkingLotDetailResp'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        租户内按 name + deleted_at IS NULL 唯一索引去重；创建默认区域时同步初始化 fee_rule。
    get:
      summary: 查询停车场列表
      description: 按租户、状态、关键字分页查询车场。
      operationId: listParkingLots
      tags:
        - 车场管理
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: keyword
          in: query
          schema:
            type: string
            maxLength: 50
          description: 车场名称/地址模糊搜索
        - name: status
          in: query
          schema:
            type: string
            enum: [OPEN, PAUSED, UPGRADING]
        - name: pageNum
          in: query
          required: true
          schema:
            type: integer
            minimum: 1
            default: 1
        - name: pageSize
          in: query
          required: true
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/PageResultParkingLot'

  /api/v1/parking-lots/{lotId}:
    get:
      summary: 获取停车场详情
      description: 返回车场档案、关联区域与通道概览。
      operationId: getParkingLot
      tags:
        - 车场管理
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      responses:
        '200':
          $ref: '#/components/responses/ParkingLotDetailResp'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'

  /api/v1/parking-lots/{lotId}/zones:
    post:
      summary: 创建区域
      description: 在指定车场下创建停车区域，区域车位总数不得超过车场总车位数。
      operationId: createParkingZone
      tags:
        - 区域管理
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ParkingZoneCreateReq'
            example:
              name: B1层普通区
              tag: NORMAL
              level: 1
              feeRuleId: 2001
              totalSpaces: 100
              fixedSpaces: 30
              status: ENABLED
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/ParkingZoneVO'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: parking:lot:{lotId}:zone:create；扣减车场剩余可分配车位使用 parking_lot.version 乐观锁。
    get:
      summary: 查询区域列表
      description: 按车场查询区域分页列表。
      operationId: listParkingZones
      tags:
        - 区域管理
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
        - name: status
          in: query
          schema:
            type: string
            enum: [ENABLED, DISABLED]
        - name: pageNum
          in: query
          required: true
          schema:
            type: integer
            minimum: 1
            default: 1
        - name: pageSize
          in: query
          required: true
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/PageResultParkingZone'

  /api/v1/parking-lots/{lotId}/lanes:
    post:
      summary: 创建通道
      description: 创建入口/出口/双向通道，并绑定入口相机/出口相机。
      operationId: createParkingLane
      tags:
        - 通道管理
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ParkingLaneCreateReq'
            example:
              zoneId: 3001
              laneNo: A1
              name: 东大门
              type: ENTRY
              entryCameraId: 8001
              exitCameraId: null
              status: ENABLED
              tideMode: CLOSED
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/ParkingLaneVO'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: parking:lot:{lotId}:lane:create；lane_no + lot_id + deleted_at IS NULL 唯一索引防并发重复。
    get:
      summary: 查询通道列表
      description: 按车场/区域查询通道列表。
      operationId: listParkingLanes
      tags:
        - 通道管理
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
        - name: zoneId
          in: query
          schema:
            type: integer
            format: int64
        - name: type
          in: query
          schema:
            type: string
            enum: [ENTRY, EXIT, BIDIRECTIONAL]
        - name: pageNum
          in: query
          required: true
          schema:
            type: integer
            minimum: 1
            default: 1
        - name: pageSize
          in: query
          required: true
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/PageResultParkingLane'

  /api/v1/parking-lots/{lotId}/vehicles:
    post:
      summary: 车辆登记
      description: |
        登记免费车、固定车（月租车）、储值车、贵宾车、超级车牌。车牌入参会自动转大写；
        固定车登记需校验绑定车位是否空闲。
      operationId: registerVehicle
      tags:
        - 车辆登记
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/VehicleRegisterReq'
            example:
              plateNumber: 京A12345
              plateColor: BLUE
              type: MONTH
              ownerName: 张三
              phone: "13800138001"
              departmentId: 5001
              parkingSpaceName: A-001
              feeRuleId: 2001
              billingCycle: MONTH
              startDate: "2026-07-13"
              endDate: "2027-07-12"
              allowRenewal: true
              renewalMode: EXTEND
              countSpaces: true
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/VehicleVO'
        '400':
          $ref: '#/components/responses/BadRequest'
        '403':
          $ref: '#/components/responses/Forbidden'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: vehicle:register:{lotId}:{plateNumber}；固定车位占用使用 parking_space.version 乐观锁。

  /api/v1/parking-lots/{lotId}/fee-rules:
    post:
      summary: 创建收费规则
      description: |
        支持按时/按次/阶梯/分时段计费；区域未指定时作用于车场下所有区域。
        同一区域同一时间只能有一条生效规则。
      operationId: createFeeRule
      tags:
        - 收费标准
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/FeeRuleCreateReq'
            example:
              zoneId: 3001
              name: 商场标准收费
              billingMode: HOURLY
              freeMinutes: 15
              unitMinutes: 30
              firstPeriodPrice: "500"
              subsequentPrice: "200"
              dailyCap: "5000"
              nightCap: "2000"
              status: ENABLED
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/FeeRuleVO'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: fee:rule:create:{lotId}:{zoneId}；数据库 (lot_id, zone_id, billing_mode, deleted_at) 唯一索引 + version 乐观锁。

  /api/v1/fee/calculate:
    post:
      summary: 停车费用计算
      description: 根据车场/区域、车辆类型、入场时间、出场时间计算应收金额。
      operationId: calculateFee
      tags:
        - 收费计算
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/FeeCalculateReq'
            example:
              lotId: 1001
              zoneId: 3001
              plateNumber: 京A12345
              vehicleType: TEMP
              entryTime: "2026-07-12T14:32:18+08:00"
              exitTime: "2026-07-12T16:45:22+08:00"
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/FeeCalculateResp'
        '400':
          $ref: '#/components/responses/BadRequest'

  /api/v1/access/entry:
    post:
      summary: 车辆入场
      description: |
        岗亭/相机识别后调用。完成车牌匹配、车位满校验、黑名单拦截、开闸指令下发、入场记录写入。
        开闸指令待设备协议完善后接入，当前按【预留】处理，记录 UNCERTAIN 状态。
      operationId: vehicleEntry
      tags:
        - 车辆进出
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AccessEntryReq'
            example:
              lotId: 1001
              laneId: 7001
              plateNumber: 京A12345
              plateColor: BLUE
              entryTime: "2026-07-12T14:32:18+08:00"
              captureImage: https://oss.example.com/capture/xxx.jpg
              recognitionType: AUTO
              confidence: 98
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/AccessEntryResp'
        '400':
          $ref: '#/components/responses/BadRequest'
        '403':
          $ref: '#/components/responses/Forbidden'
        '504':
          $ref: '#/components/responses/GatewayTimeout'
      x-concurrency: |
        Redis 锁 key: access:entry:{lotId}:{plateNumber}，TTL 5s；车位余量使用 parking_lot_spaces.version 乐观锁扣减。
      x-device-note: |
        开闸为当前 Device Access v0.2 未实现能力，平台侧禁止真实调用；接口返回 mock 结果并记录 UNCERTAIN 审计。

  /api/v1/access/exit:
    post:
      summary: 车辆出场
      description: |
        岗亭/相机识别后调用。匹配在场记录、计算费用、生成/关联订单、出场记录写入。
        月卡/VIP/免费车自动放行；临时车生成待支付订单。
      operationId: vehicleExit
      tags:
        - 车辆进出
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AccessExitReq'
            example:
              lotId: 1001
              laneId: 7002
              plateNumber: 京A12345
              exitTime: "2026-07-12T16:45:22+08:00"
              captureImage: https://oss.example.com/capture/yyy.jpg
              recognitionType: AUTO
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/AccessExitResp'
        '400':
          $ref: '#/components/responses/BadRequest'
        '403':
          $ref: '#/components/responses/Forbidden'
        '504':
          $ref: '#/components/responses/GatewayTimeout'
      x-concurrency: |
        Redis 锁 key: access:exit:{lotId}:{plateNumber}，TTL 5s；订单创建使用订单号幂等 + 在场记录 version 乐观锁。

  /api/v1/orders:
    post:
      summary: 创建停车收费订单
      description: |
        根据出场请求或岗亭手动创建收费订单。支持优惠券/积分抵扣预计算，但真正的核销在 /discounts 接口。
      operationId: createOrder
      tags:
        - 订单支付
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OrderCreateReq'
            example:
              lotId: 1001
              laneId: 7002
              plateNumber: 京A12345
              plateColor: BLUE
              vehicleType: TEMP
              entryTime: "2026-07-12T14:32:18+08:00"
              exitTime: "2026-07-12T16:45:22+08:00"
              parkingDuration: 133
              originalAmount: "1500"
              feeRuleId: 2001
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/OrderVO'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: order:create:{lotId}:{plateNumber}；订单号按 lotId + 时间 + 序号生成，数据库唯一索引保证幂等。

  /api/v1/orders/{orderId}/pay:
    post:
      summary: 订单支付（P云交易预请求）
      description: |
        调用P云开放平台交易预请求接口（/gate/1.0/payment/trade/prepare），获取P云支付流水 pay_serial。
        同一 orderId 仅允许支付一次；支付回调幂等由后端按 pay_order 保证。
        当前一期仅支持P云聚合支付；微信/支付宝直连标注【预留】。
      operationId: payOrder
      tags:
        - 订单支付
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: orderId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OrderPayReq'
            example:
              payChannel: PYUN
              payerOpenId: oabc123
              payAmount: "800"
              scene: MINI_APP
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/OrderPayResp'
        '400':
          $ref: '#/components/responses/BadRequest'
        '402':
          description: 需要支付
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Result'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: order:pay:{orderId}，TTL 30s；支付渠道流水号唯一索引防止重复支付。
      x-reserved: |
        payChannel 支持 PYUN(一期实现，P云聚合支付)、WECHAT(预留，微信直连)、ALIPAY(预留，支付宝直连)、UNIONPAY(预留，银联直连)。

  /api/v1/coupons/available:
    get:
      summary: 查询可用优惠券
      description: 根据车场、车牌、订单金额查询用户当前可使用的优惠券列表。
      operationId: listAvailableCoupons
      tags:
        - 优惠券/积分
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: query
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
        - name: plateNumber
          in: query
          required: true
          schema:
            type: string
            pattern: '^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9]{4,6}[A-Z0-9挂学警港澳]?$'
            maxLength: 12
        - name: orderAmount
          in: query
          required: true
          schema:
            type: string
            pattern: '^[0-9]+$'
        - name: orderType
          in: query
          schema:
            type: string
            enum: [PARKING, MONTH_RENEW, VISITOR]
            default: PARKING
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        type: array
                        items:
                          $ref: '#/components/schemas/CouponVO'

  /api/v1/points/balance:
    get:
      summary: 查询积分余额
      description: 查询当前登录用户在指定租户下的可用积分、即将过期积分。
      operationId: getPointsBalance
      tags:
        - 优惠券/积分
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/PointsBalanceVO'

  /api/v1/orders/{orderId}/discounts:
    post:
      summary: 使用优惠券/积分抵扣
      description: |
        为待支付订单选择优惠券或积分抵扣，实时计算优惠后金额并冻结优惠券/积分。
        已支付/已取消订单不允许再抵扣。
      operationId: applyDiscounts
      tags:
        - 优惠券/积分
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: orderId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/DiscountApplyReq'
            example:
              userCouponId: 9001
              points: 200
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/DiscountApplyResp'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: order:discount:{orderId}；优惠券扣减使用 user_coupon.version 乐观锁；积分扣减使用 member_points.version 乐观锁。

  /api/v1/visitors/appointments:
    post:
      summary: 访客预约
      description: |
        访客小程序/被访人代申请/岗亭现场登记。提交后进入审核流程；白名单单位自动通过。
        默认前 2 小时免费，超时按临停收费。
      operationId: createVisitorAppointment
      tags:
        - 访客管理
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/VisitorAppointmentReq'
            example:
              source: MINI_APP
              visitorName: 李四
              phone: "13900139000"
              plateNumber: 京B67890
              plateColor: BLUE
              lotId: 1001
              visitedName: 王五
              visitedDepartmentId: 5101
              visitReason: 商务洽谈
              appointmentTime: "2026-07-13T09:00:00+08:00"
              endTime: "2026-07-13T11:00:00+08:00"
              chargeType: FREE
              allowEarlyEntry: true
              earlyEntryHours: 1
              overtimeStrategy: EXTEND
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/VisitorAppointmentVO'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: visitor:appointment:{lotId}:{plateNumber}:{appointmentTime:yyyyMMdd}；余位校验使用乐观锁。

  /api/v1/parking-lots/{lotId}/blacklist:
    post:
      summary: 添加黑名单
      description: |
        手动添加黑名单车辆，支持精确车牌与模糊车牌（慎用）。
        黑名单车辆入场时默认拒绝，可配置弹窗确认或告警放行。
      operationId: addBlacklist
      tags:
        - 黑名单
      parameters:
        - $ref: '#/components/parameters/IdempotencyKeyHeader'
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/BlacklistAddReq'
            example:
              plateNumber: 京A12345
              plateColor: BLUE
              reason: OVERDUE
              source: MANUAL
              validType: PERMANENT
              validEndDate: null
              releaseCondition: null
              remark: 超时停放超过 7 天
              relatedOrderIds: [100001, 100002]
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/BlacklistVO'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          $ref: '#/components/responses/Conflict'
      x-concurrency: |
        Redis 锁 key: blacklist:add:{lotId}:{plateNumber}；数据库 (lot_id, plate_number, deleted_at) 唯一索引 + version 乐观锁。

  /api/v1/parking-lots/{lotId}/spaces/remaining:
    get:
      summary: 余位查询
      description: |
        查询车场及各区域实时剩余车位。优先读取 Redis 缓存，缓存缺失时从 DB 计算。
        支持按区域、车辆类型统计。
      operationId: getRemainingSpaces
      tags:
        - 余位查询
      parameters:
        - $ref: '#/components/parameters/TenantIdHeader'
        - name: lotId
          in: path
          required: true
          schema:
            type: integer
            format: int64
            minimum: 1
        - name: zoneId
          in: query
          schema:
            type: integer
            format: int64
        - name: vehicleType
          in: query
          schema:
            type: string
            enum: [TEMP, MONTH, WALLET, FREE, VIP]
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/Result'
                  - type: object
                    properties:
                      data:
                        $ref: '#/components/schemas/RemainingSpacesResp'

components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: "Sa-Token / JWT 登录令牌，通过 Authorization: Bearer {token} 传递。"

  parameters:
    IdempotencyKeyHeader:
      name: X-Idempotency-Key
      in: header
      required: true
      schema:
        type: string
        format: uuid
        maxLength: 64
      description: 幂等键，同一键 24 小时内重复提交返回首次结果。

    TenantIdHeader:
      name: X-Tenant-Id
      in: header
      required: false
      schema:
        type: integer
        format: int64
        minimum: 1
      description: |
        目标租户 ID。普通用户从 JWT 解析，超管可显式指定以跨租户操作。

  schemas:
    Result:
      type: object
      properties:
        code:
          type: string
          description: 业务状态码，"0000" 表示成功
          example: "0000"
        message:
          type: string
          description: 提示信息
          example: success
        requestId:
          type: string
          description: 请求追踪 ID，与 X-Request-Id 一致
          example: req-202607130001
        data:
          type: object
          description: 业务数据
        timestamp:
          type: integer
          format: int64
          description: 服务器时间戳（毫秒）
          example: 1752345678901
      required: [code, message, requestId, timestamp]

    PageResult:
      type: object
      properties:
        pageNum:
          type: integer
          description: 当前页码
        pageSize:
          type: integer
          description: 每页大小
        total:
          type: integer
          format: int64
          description: 总记录数
        pages:
          type: integer
          description: 总页数
        list:
          type: array
          items:
            type: object
      required: [pageNum, pageSize, total, pages, list]

    PageResultParkingLot:
      allOf:
        - $ref: '#/components/schemas/PageResult'
        - type: object
          properties:
            list:
              type: array
              items:
                $ref: '#/components/schemas/ParkingLotVO'

    PageResultParkingZone:
      allOf:
        - $ref: '#/components/schemas/PageResult'
        - type: object
          properties:
            list:
              type: array
              items:
                $ref: '#/components/schemas/ParkingZoneVO'

    PageResultParkingLane:
      allOf:
        - $ref: '#/components/schemas/PageResult'
        - type: object
          properties:
            list:
              type: array
              items:
                $ref: '#/components/schemas/ParkingLaneVO'

    ParkingLotCreateReq:
      type: object
      properties:
        name:
          type: string
          maxLength: 100
          description: 车场名称
        companyId:
          type: integer
          format: int64
          description: 所属公司 ID
        groupId:
          type: integer
          format: int64
          description: 所属集团 ID
        regionType:
          type: string
          enum: [MALL, OFFICE, RESIDENTIAL, HOSPITAL, SCENIC, TRANSPORT]
          description: 区域类型
        province:
          type: string
          maxLength: 50
        city:
          type: string
          maxLength: 50
        district:
          type: string
          maxLength: 50
        address:
          type: string
          maxLength: 200
        longitude:
          type: string
          pattern: '^-?\d{1,3}\.\d{1,8}$'
        latitude:
          type: string
          pattern: '^-?\d{1,3}\.\d{1,8}$'
        contactName:
          type: string
          maxLength: 50
        contactPhone:
          type: string
          maxLength: 20
        status:
          type: string
          enum: [OPEN, PAUSED, UPGRADING]
        businessHoursStart:
          type: string
          pattern: '^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$'
        businessHoursEnd:
          type: string
          pattern: '^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$'
        images:
          type: array
          maxItems: 5
          items:
            type: string
            format: uri
      required: [name, companyId, groupId, regionType, province, city, district, address, longitude, latitude, contactName, contactPhone, status]

    ParkingLotVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        tenantId:
          type: integer
          format: int64
        name:
          type: string
        companyId:
          type: integer
          format: int64
        groupId:
          type: integer
          format: int64
        regionType:
          type: string
        province:
          type: string
        city:
          type: string
        district:
          type: string
        address:
          type: string
        longitude:
          type: string
        latitude:
          type: string
        contactName:
          type: string
        contactPhone:
          type: string
        status:
          type: string
        businessHoursStart:
          type: string
        businessHoursEnd:
          type: string
        totalSpaces:
          type: integer
        images:
          type: array
          items:
            type: string
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    ParkingZoneCreateReq:
      type: object
      properties:
        name:
          type: string
          maxLength: 100
        tag:
          type: string
          enum: [NORMAL, VIP, STAFF, LOADING, CHARGING]
        level:
          type: integer
          minimum: 1
          maximum: 9
        feeRuleId:
          type: integer
          format: int64
        totalSpaces:
          type: integer
          minimum: 0
        fixedSpaces:
          type: integer
          minimum: 0
        status:
          type: string
          enum: [ENABLED, DISABLED]
        managerId:
          type: integer
          format: int64
        remark:
          type: string
          maxLength: 500
      required: [name, tag, level, feeRuleId, totalSpaces, fixedSpaces, status]

    ParkingZoneVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        lotId:
          type: integer
          format: int64
        name:
          type: string
        tag:
          type: string
        level:
          type: integer
        feeRuleId:
          type: integer
          format: int64
        totalSpaces:
          type: integer
        fixedSpaces:
          type: integer
        tempSpaces:
          type: integer
        status:
          type: string
        managerId:
          type: integer
          format: int64
        remark:
          type: string
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    ParkingLaneCreateReq:
      type: object
      properties:
        zoneId:
          type: integer
          format: int64
        laneNo:
          type: string
          maxLength: 20
        name:
          type: string
          maxLength: 100
        type:
          type: string
          enum: [ENTRY, EXIT, BIDIRECTIONAL]
        entryCameraId:
          type: integer
          format: int64
        exitCameraId:
          type: integer
          format: int64
        status:
          type: string
          enum: [ENABLED, DISABLED, MAINTENANCE]
        tideMode:
          type: string
          enum: [CLOSED, MORNING_ENTRY, EVENING_EXIT]
      required: [zoneId, laneNo, name, type, status]

    ParkingLaneVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        lotId:
          type: integer
          format: int64
        zoneId:
          type: integer
          format: int64
        laneNo:
          type: string
        name:
          type: string
        type:
          type: string
        entryCameraId:
          type: integer
          format: int64
        exitCameraId:
          type: integer
          format: int64
        status:
          type: string
        tideMode:
          type: string
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    VehicleRegisterReq:
      type: object
      properties:
        plateNumber:
          type: string
          pattern: '^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9]{4,6}[A-Z0-9挂学警港澳]?$'
          maxLength: 12
        plateColor:
          type: string
          enum: [BLUE, YELLOW, GREEN, WHITE, BLACK, GRADIENT_GREEN]
        type:
          type: string
          enum: [FREE, MONTH, WALLET, VIP, SUPER]
        ownerName:
          type: string
          maxLength: 50
        phone:
          type: string
          pattern: '^1[3-9]\d{9}$'
        personnelNo:
          type: string
          maxLength: 50
        departmentId:
          type: integer
          format: int64
        parkingSpaceName:
          type: string
          maxLength: 50
        feeRuleId:
          type: integer
          format: int64
        billingCycle:
          type: string
          enum: [MONTH, QUARTER, HALF_YEAR, YEAR]
        startDate:
          type: string
          format: date
        endDate:
          type: string
          format: date
        currentBalance:
          type: string
          pattern: '^[0-9]+$'
          description: 储值车开户金额，整数分
        discount:
          type: string
          pattern: '^(0\.\d{1,2}|1(\.0{1,2})?)$'
        allowRenewal:
          type: boolean
        renewalMode:
          type: string
          enum: [EXTEND, NOW]
        countSpaces:
          type: boolean
        benefits:
          type: array
          items:
            type: string
            enum: [FREE_CHARGE, IGNORE_FULL, EXCLUSIVE_LANE, RESERVED_SPACE, NO_APPOINTMENT]
        vipLevel:
          type: string
          enum: [LEVEL_1, LEVEL_2, LEVEL_3]
        validType:
          type: string
          enum: [LONG_TERM, RANGE]
        validStartDate:
          type: string
          format: date
        validEndDate:
          type: string
          format: date
        remark:
          type: string
          maxLength: 500
      required: [plateNumber, plateColor, type]

    VehicleVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        lotId:
          type: integer
          format: int64
        plateNumber:
          type: string
        plateColor:
          type: string
        type:
          type: string
        ownerName:
          type: string
        phone:
          type: string
        departmentId:
          type: integer
          format: int64
        parkingSpaceName:
          type: string
        feeRuleId:
          type: integer
          format: int64
        startDate:
          type: string
          format: date
        endDate:
          type: string
          format: date
        status:
          type: string
          enum: [NORMAL, EXPIRED, DISABLED]
        createdAt:
          type: string
          format: date-time

    FeeRuleCreateReq:
      type: object
      properties:
        zoneId:
          type: integer
          format: int64
        name:
          type: string
          maxLength: 100
        billingMode:
          type: string
          enum: [HOURLY, PER_ENTRY, TIERED, TIME_SEGMENT]
        freeMinutes:
          type: integer
          minimum: 0
        unitMinutes:
          type: integer
          minimum: 1
        firstPeriodPrice:
          type: string
          pattern: '^[0-9]+$'
          description: 整数分
        subsequentPrice:
          type: string
          pattern: '^[0-9]+$'
        dailyCap:
          type: string
          pattern: '^[0-9]+$'
        nightCap:
          type: string
          pattern: '^[0-9]+$'
        timeSegments:
          type: array
          items:
            $ref: '#/components/schemas/TimeSegment'
        holidayRules:
          type: array
          items:
            $ref: '#/components/schemas/HolidayRule'
        status:
          type: string
          enum: [ENABLED, DISABLED]
      required: [name, billingMode, status]

    TimeSegment:
      type: object
      properties:
        startTime:
          type: string
          pattern: '^([01]\d|2[0-3]):[0-5]\d$'
        endTime:
          type: string
          pattern: '^([01]\d|2[0-3]):[0-5]\d$'
        unitPrice:
          type: string
          pattern: '^[0-9]+$'
        capAmount:
          type: string
          pattern: '^[0-9]+$'
      required: [startTime, endTime, unitPrice]

    HolidayRule:
      type: object
      properties:
        holidayType:
          type: string
          enum: [LEGAL, NON_LEGAL]
        startDate:
          type: string
          format: date
        endDate:
          type: string
          format: date
        unitPrice:
          type: string
          pattern: '^[0-9]+$'
      required: [holidayType, startDate, endDate, unitPrice]

    FeeRuleVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        lotId:
          type: integer
          format: int64
        zoneId:
          type: integer
          format: int64
        name:
          type: string
        billingMode:
          type: string
        freeMinutes:
          type: integer
        unitMinutes:
          type: integer
        firstPeriodPrice:
          type: string
        subsequentPrice:
          type: string
        dailyCap:
          type: string
        nightCap:
          type: string
        timeSegments:
          type: array
          items:
            $ref: '#/components/schemas/TimeSegment'
        holidayRules:
          type: array
          items:
            $ref: '#/components/schemas/HolidayRule'
        status:
          type: string
        createdAt:
          type: string
          format: date-time

    FeeCalculateReq:
      type: object
      properties:
        lotId:
          type: integer
          format: int64
        zoneId:
          type: integer
          format: int64
        plateNumber:
          type: string
          maxLength: 12
        vehicleType:
          type: string
          enum: [TEMP, MONTH, WALLET, FREE, VIP, SUPER, VISITOR]
        entryTime:
          type: string
          format: date-time
        exitTime:
          type: string
          format: date-time
      required: [lotId, plateNumber, vehicleType, entryTime, exitTime]

    FeeCalculateResp:
      type: object
      properties:
        lotId:
          type: integer
          format: int64
        zoneId:
          type: integer
          format: int64
        plateNumber:
          type: string
        vehicleType:
          type: string
        parkingDuration:
          type: integer
          description: 停车时长，分钟
        freeMinutes:
          type: integer
        billingDuration:
          type: integer
          description: 计费时长，分钟
        originalAmount:
          type: string
          description: 原始应收，整数分
        discountAmount:
          type: string
        payableAmount:
          type: string
        feeRuleId:
          type: integer
          format: int64
        feeRuleName:
          type: string
        detail:
          type: array
          items:
            type: object
            properties:
              period:
                type: string
              duration:
                type: integer
              unitPrice:
                type: string
              amount:
                type: string

    AccessEntryReq:
      type: object
      properties:
        lotId:
          type: integer
          format: int64
        laneId:
          type: integer
          format: int64
        plateNumber:
          type: string
          maxLength: 12
        plateColor:
          type: string
          enum: [BLUE, YELLOW, GREEN, WHITE, BLACK, GRADIENT_GREEN, UNKNOWN]
        entryTime:
          type: string
          format: date-time
        captureImage:
          type: string
          format: uri
        recognitionType:
          type: string
          enum: [AUTO, MANUAL, SCAN, BLUETOOTH, UNKNOWN_PLATE]
        confidence:
          type: integer
          minimum: 0
          maximum: 100
        operatorId:
          type: integer
          format: int64
      required: [lotId, laneId, plateNumber, entryTime, recognitionType]

    AccessEntryResp:
      type: object
      properties:
        accessLogId:
          type: integer
          format: int64
        plateNumber:
          type: string
        vehicleType:
          type: string
        matchResult:
          type: string
          enum: [NORMAL, EXPIRED, BLACKLIST, FULL, NO_PERMISSION, UNKNOWN]
        allowEntry:
          type: boolean
        gateAction:
          type: string
          enum: [OPEN, CLOSE, UNCERTAIN]
          description: UNCERTAIN 表示设备响应未知
        message:
          type: string
        remainingSpaces:
          type: integer

    AccessExitReq:
      type: object
      properties:
        lotId:
          type: integer
          format: int64
        laneId:
          type: integer
          format: int64
        plateNumber:
          type: string
          maxLength: 12
        plateColor:
          type: string
          enum: [BLUE, YELLOW, GREEN, WHITE, BLACK, GRADIENT_GREEN, UNKNOWN]
        exitTime:
          type: string
          format: date-time
        captureImage:
          type: string
          format: uri
        recognitionType:
          type: string
          enum: [AUTO, MANUAL, SCAN, BLUETOOTH, UNKNOWN_PLATE]
        operatorId:
          type: integer
          format: int64
      required: [lotId, laneId, plateNumber, exitTime, recognitionType]

    AccessExitResp:
      type: object
      properties:
        accessLogId:
          type: integer
          format: int64
        plateNumber:
          type: string
        vehicleType:
          type: string
        matchResult:
          type: string
          enum: [NORMAL, EXPIRED, NO_ENTRY_RECORD, BALANCE_INSUFFICIENT, BLACKLIST, UNKNOWN]
        allowExit:
          type: boolean
        orderId:
          type: integer
          format: int64
        originalAmount:
          type: string
        payableAmount:
          type: string
        gateAction:
          type: string
          enum: [OPEN, CLOSE, UNCERTAIN]
        message:
          type: string

    OrderCreateReq:
      type: object
      properties:
        lotId:
          type: integer
          format: int64
        laneId:
          type: integer
          format: int64
        plateNumber:
          type: string
          maxLength: 12
        plateColor:
          type: string
        vehicleType:
          type: string
        entryTime:
          type: string
          format: date-time
        exitTime:
          type: string
          format: date-time
        parkingDuration:
          type: integer
          description: 停车时长，分钟
        originalAmount:
          type: string
          pattern: '^[0-9]+$'
        feeRuleId:
          type: integer
          format: int64
        accessLogId:
          type: integer
          format: int64
        operatorId:
          type: integer
          format: int64
      required: [lotId, laneId, plateNumber, vehicleType, entryTime, exitTime, parkingDuration, originalAmount, feeRuleId]

    OrderVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        orderNo:
          type: string
        orderType:
          type: string
        status:
          type: string
          enum: [PENDING_PAY, PAYING, PAID, COMPLETED, CANCELLED, PAY_FAILED, REFUNDING, REFUNDED]
        lotId:
          type: integer
          format: int64
        plateNumber:
          type: string
        parkingDuration:
          type: integer
        originalAmount:
          type: string
        discountAmount:
          type: string
        pointsDiscount:
          type: string
        payableAmount:
          type: string
        paidAmount:
          type: string
        payChannel:
          type: string
        payTime:
          type: string
          format: date-time
        expiredAt:
          type: string
          format: date-time
        createdAt:
          type: string
          format: date-time

    OrderPayReq:
      type: object
      properties:
        payChannel:
          type: string
          enum: [PYUN, WECHAT, ALIPAY, UNIONPAY, CASH, BALANCE]
          description: PYUN 一期实现（P云聚合支付），WECHAT/ALIPAY/UNIONPAY 预留
        payerOpenId:
          type: string
          maxLength: 64
        payAmount:
          type: string
          pattern: '^[0-9]+$'
        scene:
          type: string
          enum: [MINI_APP, H5, POS]
      required: [payChannel, payAmount, scene]

    OrderPayResp:
      type: object
      properties:
        orderId:
          type: integer
          format: int64
        orderNo:
          type: string
        payChannel:
          type: string
        payAmount:
          type: string
        status:
          type: string
        prepayParams:
          type: object
          description: P云交易预请求返回参数（一期）；微信/支付宝直连预留字段
          properties:
            paySerial:
              type: string
              description: P云支付流水
            appId:
              type: string
            timeStamp:
              type: string
            nonceStr:
              type: string
            package:
              type: string
            signType:
              type: string
            paySign:
              type: string

    CouponVO:
      type: object
      properties:
        userCouponId:
          type: integer
          format: int64
        couponName:
          type: string
        couponType:
          type: string
          enum: [AMOUNT, DISCOUNT, FREE_TIME, FREE_PARKING, INSTANT]
        discountValue:
          type: string
          description: 减免时长(分)/金额(分)/折扣(0.85)
        minOrderAmount:
          type: string
        validStart:
          type: string
          format: date-time
        validEnd:
          type: string
          format: date-time
        applicableLots:
          type: array
          items:
            type: integer
            format: int64

    PointsBalanceVO:
      type: object
      properties:
        userId:
          type: integer
          format: int64
        tenantId:
          type: integer
          format: int64
        totalPoints:
          type: integer
          description: 总积分
        availablePoints:
          type: integer
          description: 可用积分
        frozenPoints:
          type: integer
          description: 冻结积分
        expiringSoonPoints:
          type: integer
          description: 7 天内过期积分
        exchangeRate:
          type: string
          description: 积分抵扣比例，如 100 积分 = 1 元

    DiscountApplyReq:
      type: object
      properties:
        userCouponId:
          type: integer
          format: int64
        points:
          type: integer
          minimum: 0
      anyOf:
        - required: [userCouponId]
        - required: [points]

    DiscountApplyResp:
      type: object
      properties:
        orderId:
          type: integer
          format: int64
        originalAmount:
          type: string
        couponDiscount:
          type: string
        pointsDiscount:
          type: string
        pointsUsed:
          type: integer
        payableAmount:
          type: string

    VisitorAppointmentReq:
      type: object
      properties:
        source:
          type: string
          enum: [MINI_APP, EMPLOYEE, UNIT_BATCH, ONSITE]
        visitorName:
          type: string
          maxLength: 50
        phone:
          type: string
          pattern: '^1[3-9]\d{9}$'
        plateNumber:
          type: string
          maxLength: 12
        plateColor:
          type: string
          enum: [BLUE, YELLOW, GREEN, WHITE, BLACK, GRADIENT_GREEN]
        unitId:
          type: integer
          format: int64
        unitName:
          type: string
          maxLength: 100
        visitedName:
          type: string
          maxLength: 50
        visitedDepartmentId:
          type: integer
          format: int64
        visitReason:
          type: string
          maxLength: 200
        visitorCount:
          type: integer
          minimum: 1
        appointmentTime:
          type: string
          format: date-time
        endTime:
          type: string
          format: date-time
        chargeType:
          type: string
          enum: [FREE, TEMP, FIXED_AMOUNT]
        fixedAmount:
          type: string
          pattern: '^[0-9]+$'
        allowEarlyEntry:
          type: boolean
        earlyEntryHours:
          type: integer
          minimum: 0
          maximum: 24
        overtimeStrategy:
          type: string
          enum: [EXTEND, REJECT, CONFIRM]
        materials:
          type: array
          items:
            type: string
            format: uri
      required: [source, visitorName, phone, plateNumber, lotId, visitedName, visitedDepartmentId, visitReason, appointmentTime, endTime, chargeType, overtimeStrategy]

    VisitorAppointmentVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        visitorNo:
          type: string
        status:
          type: string
          enum: [PENDING, APPROVING, APPROVED, REJECTED, CANCELLED]
        visitorName:
          type: string
        plateNumber:
          type: string
        appointmentTime:
          type: string
          format: date-time
        endTime:
          type: string
          format: date-time
        auditRemark:
          type: string
        createdAt:
          type: string
          format: date-time

    BlacklistAddReq:
      type: object
      properties:
        plateNumber:
          type: string
          maxLength: 12
        plateColor:
          type: string
          enum: [BLUE, YELLOW, GREEN, WHITE, BLACK, GRADIENT_GREEN]
        reason:
          type: string
          enum: [OVERDUE, UNPAID, VIOLATION, MANUAL, OTHER]
        source:
          type: string
          enum: [MANUAL, AUTO_OVERDUE, AUTO_UNPAID]
        validType:
          type: string
          enum: [PERMANENT, RANGE, CONDITIONAL]
        validEndDate:
          type: string
          format: date
        releaseCondition:
          type: string
          maxLength: 200
        remark:
          type: string
          maxLength: 500
        relatedOrderIds:
          type: array
          items:
            type: integer
            format: int64
      required: [plateNumber, reason, source, validType]

    BlacklistVO:
      type: object
      properties:
        id:
          type: integer
          format: int64
        lotId:
          type: integer
          format: int64
        plateNumber:
          type: string
        reason:
          type: string
        source:
          type: string
        validType:
          type: string
        validEndDate:
          type: string
          format: date
        status:
          type: string
          enum: [ACTIVE, RELEASED]
        operatorId:
          type: integer
          format: int64
        createdAt:
          type: string
          format: date-time

    RemainingSpacesResp:
      type: object
      properties:
        lotId:
          type: integer
          format: int64
        totalSpaces:
          type: integer
        occupiedSpaces:
          type: integer
        remainingSpaces:
          type: integer
        reservedSpaces:
          type: integer
        lastUpdated:
          type: string
          format: date-time
        zones:
          type: array
          items:
            type: object
            properties:
              zoneId:
                type: integer
                format: int64
              zoneName:
                type: string
              totalSpaces:
                type: integer
              remainingSpaces:
                type: integer

  responses:
    ParkingLotDetailResp:
      description: 车场详情响应
      content:
        application/json:
          schema:
            allOf:
              - $ref: '#/components/schemas/Result'
              - type: object
                properties:
                  data:
                    $ref: '#/components/schemas/ParkingLotVO'

    BadRequest:
      description: 参数校验失败
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Result'
          example:
            code: "1001"
            message: "参数校验失败: name 不能为空"
            requestId: "req-202607130001"
            data: null
            timestamp: 1752345678901

    Conflict:
      description: 请求重复或资源状态冲突
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Result'
          example:
            code: "1002"
            message: "幂等键已存在"
            requestId: "req-202607130001"
            data: null
            timestamp: 1752345678901

    Forbidden:
      description: 禁止访问（权限/数据范围不足或跨租户）
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Result'
          example:
            code: "2002"
            message: "禁止访问：无权操作该车场"
            requestId: "req-202607130001"
            data: null
            timestamp: 1752345678901

    NotFound:
      description: 资源不存在
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Result'
          example:
            code: "1003"
            message: "资源不存在"
            requestId: "req-202607130001"
            data: null
            timestamp: 1752345678901

    GatewayTimeout:
      description: 设备离线或指令超时
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Result'
          example:
            code: "5001"
            message: "设备离线或指令超时"
            requestId: "req-202607130001"
            data:
              gateAction: UNCERTAIN
            timestamp: 1752345678901
```

## 4.4 关键接口补充说明

### 4.4.1 车辆入场 /access/entry 业务规则

1. 车牌自动转大写，按 GA36-2018 正则校验。
2. 匹配顺序：**黑名单 > 超级车牌 > VIP > 固定车/月租车 > 储值车 > 免费车 > 临时车 > 访客**。
3. 黑名单车辆默认拒绝，LED 提示"车辆已被限制"；岗亭可人工确认放行并记录告警。
4. 车位满时按车场策略处理：禁止入场 / 白名单可入 / 全部可入。
5. 重复入场按车场策略：覆盖入场时间（默认）/ 拒绝 / 弹窗确认 / 不开闸等。
6. 开闸结果：
   - 成功 → `gateAction=OPEN`
   - 设备未响应 → `gateAction=UNCERTAIN`，记录审计日志，岗亭人工处置。
7. 入场成功后扣减 Redis 车位余量并异步写 DB。

### 4.4.2 车辆出场 /access/exit 业务规则

1. 优先按出场车牌查询车辆登记；若匹配失败，按车场配置的"出场查询策略"使用入场记录车牌查询。
2. 无入场记录出场：按车场策略选择"按停留时长计费 / 免费放行 / 拒绝 / 弹窗确认"。
3. 月卡/VIP/免费车：自动放行，不生成收费订单。
4. 储值车：余额充足时扣减余额放行；不足时按临时车处理。
5. 临时车/补缴场景：生成 `PENDING_PAY` 订单，返回 `payableAmount`。
6. 出场成功后回 Redis 车位余量并异步写 DB。

### 4.4.3 订单创建与支付

1. 订单号生成规则：`O{lotId}{yyyyMMdd}{6位序号}`，数据库唯一索引保证幂等。
2. 订单有效期默认 15 分钟，超时自动取消。
3. 支付接口 `payChannel=PYUN` 一期实现（P云聚合支付）；`WECHAT`/`ALIPAY`/`UNIONPAY`/`BALANCE` 标注【预留】，返回 mock。
4. P云支付回调必须验签（验证 `sign` 字段），校验 `pay_order`、`merchant`、`value`，重复回调幂等。
5. 支付成功不直接开闸，出场流程通过 /access/exit 独立校验订单状态后放行。

### 4.4.4 优惠券/积分抵扣

1. `/coupons/available` 仅返回当前用户、当前车场、满足门槛且未过期的优惠券。
2. `/orders/{orderId}/discounts` 提交后冻结优惠券/积分，订单支付时正式核销。
3. 优惠券与商家优惠默认互斥，与积分默认可叠加；具体规则以车场配置为准。
4. 积分抵扣比例由 `points_config` 配置，如 100 积分 = 1 元，抵扣上限由车场配置。

### 4.4.5 访客预约

1. 白名单单位自动通过；普通单位需三级管理员审核；敏感时段需二级管理员审核。
2. 审核通过后自动增量下发到岗亭（脱机车牌下发）。
3. 默认前 2 小时免费，超时按临停收费；`chargeType=FIXED_AMOUNT` 时按固定金额收费。
4. 预约时段冲突使用 Redis 分布式锁 + 数据库唯一索引（lotId + plateNumber + 日期）防止重复预约。

### 4.4.6 黑名单

1. 支持精确车牌与模糊车牌（如 `京A*`），模糊匹配慎用并需二级管理员审批。
2. 黑名单有效期支持永久、指定日期、条件解除。
3. 黑名单车辆通行时，岗亭端弹窗提示原因、关联欠费订单，支持人工放行并记录告警。

### 4.4.7 余位查询

1. 优先读取 Redis 实时缓存，Key：`parking:spaces:{lotId}`。
2. 缓存缺失时从 DB 聚合 `parking_zone` 与在场车辆数计算。
3. 按区域返回余位，支持按车辆类型过滤（固定车位不对外显示为空闲）。

## 4.5 错误码速查表

| 错误码 | 含义 | 适用接口示例 |
|--------|------|-------------|
| `0000` | 成功 | 全部 |
| `1001` | 参数校验失败 | 全部写接口 |
| `1002` | 请求重复/幂等键冲突 | POST 接口 |
| `1003` | 资源不存在 | GET /{id} |
| `1004` | 资源状态不允许操作 | 支付已取消订单 |
| `2001` | 未授权 | 全部 |
| `2002` | 禁止访问（权限/数据范围不足） | 三级管理员跨车场 |
| `2003` | 跨租户访问被拒绝 | 超管未授权 |
| `3001` | 车场/区域/通道状态异常 | 入场/出场 |
| `3002` | 车位已满 | /access/entry |
| `3003` | 车辆类型不匹配 | 费用计算 |
| `4001` | 收费规则计算异常 | /fee/calculate |
| `4002` | 订单状态异常 | /orders/{id}/pay |
| `4003` | 支付失败 | /orders/{id}/pay |
| `4004` | 优惠券不可用 | /orders/{id}/discounts |
| `4005` | 积分不足 | /orders/{id}/discounts |
| `5001` | 设备离线或指令超时 | /access/entry、/access/exit |
| `9001` | 系统内部错误 | 全部 |
| `9002` | 服务降级/限流 | 全部 |
