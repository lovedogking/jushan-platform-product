# P云开放平台 - 停车SaaS支付对接完整文档

> 本文档整合自 P云开放平台官方文档
> - 非PP前端文档: https://doc.4pyun.com/openapi/guideline/payment-policy.html
> - PP前端文档: https://doc.4pyun.com/parking/
> 
> 整理时间: 2026年7月
> 文档版本: v1.0

---

# 目录

## 第一篇：非PP前端接口（开放平台直连）

### 1. 接入指引
- 1.4 支付接入要求

### 2. 支付平台
- 2.1 被扫交易请求
- 2.2 发起交易预请求
- 2.3 查询交易状态
- 2.4 发起订单退款
- 2.5 退款订单查询
- 2.6 支付结果异步通知
- 2.7 结算记录同步接口
- 2.8 结算帐户信息查询接口
- 2.9 支付渠道配置信息查询
- 2.10 触发交易清算
- 2.11 分账交易同步
- 2.12 分润交易同步

### 3. 电子发票
- 3.1 提交开票申请
- 3.2 查询开票详情
- 3.3 开票结果异步通知

---

## 第二篇：PP前端接口（停车场系统接入）

### 1. 接入指引
- 车场接入文档首页
- 网络说明

### 2. 临停缴费
- 2.1 获取临停缴费订单
- 2.2 同步临停缴费通知
- 2.3 被扫/无感/ETC扣款请求
- 2.4 停车订单查询
- 2.5 停车费用计算

### 3. 固定车管理
- 3.1 车辆信息查询
- 3.2 车辆续费结果通知
- 3.3 获取固定车类型
- 3.4 新增黑名单车辆
- 3.5 删除黑名单车辆
- 3.6 新增固定车辆
- 3.7 删除固定车辆
- 3.8 更新固定车辆
- 3.9 固定车被动同步
- 3.10 固定车主动同步
- 3.11 黑名单车辆主动同步
- 3.12 固定车续费记录主动同步

### 4. 停车优惠
- 4.1 发放停车优惠
- 4.2 撤销停车优惠
- 4.3 同步停车优惠状态
- 4.4 商户优惠券状态核销

### 5. 预约停车
- 5.1 预约停车申请
- 5.2 取消预约停车

### 6. 停车管理
- 6.1 人工/无牌车入场
- 6.2 推送车辆预入场
- 6.3 推送车辆入场
- 6.4 推送停车信息更新
- 6.5 推送车辆离场
- 6.6 无感停车状态同步
- 6.7 获取停车记录详情
- 6.8 更新场内车辆信息
- 6.9 查询车场状态
- 6.10 推送异常放行记录
- 6.11 拦截车辆

### 7. 设备管理
- 7.1 车场设备列表
- 7.2 车场通道抓拍
- 7.3 车场通道状态查询
- 7.4 车场通道控制

### 8. 车场进件
- 8.1 提交车场进件

---


# ==================== 第一篇：非PP前端接口（开放平台直连） ====================

---

## 7. 支付接入要求(确认)

**原文链接**: https://doc.4pyun.com/openapi/guideline/payment-policy.html

### 7.1 公告

```
2024-01-01
```

**尊敬的P云战略合作伙伴:**

为响应政府的号召，未按照政府要求接入数据的车场，我司将在2024年4月1日关闭支付权限，届时会导致车场无法支付。为避免给您造成不便，恳请合作伙伴们及时按照以下接口接入数据：

| 事项 | 接入要求 | 相关接口 |
|------|----------|----------|
| 下单传递交易场景信息 | 需接入 | 被扫请求<br>交易预请求 |
| 上报停车记录 | 需接入 | 车辆入场<br>车辆离场<br>车场信息查询 |



---

# 2. 支付平台接口

> 以下接口为P云开放平台支付平台核心接口，包括被扫交易、交易预请求、交易查询、退款等。

## 1. 被扫交易请求

**接口URL:**
```
POST https://papi.4pyun.com/gate/1.0/payment/trade/create
```

**接口说明:**
```
微信支付宝被扫必须传auth_code，注意这种情况下不传channel，平台自动识别微信支付宝!
```

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|---------|---------|------|------|------|
| app_id | 平台分配的接入应用ID | string | Y | op1234567723122 |
| merchant | 停车场商户号 | string | Y | 6262666666 |
| pay_order | 发起支付订单号，同一个app_id下唯一不能重复 | string | Y | PAYORDER-XXXXXXXX |
| subject | 商品名称 | string | Y | 支付XX项目 |
| value | 支付金额, 单位分 | string | Y | 100 |
| channel | 支付渠道，普通微信支付宝被扫的时候不传，其它类型类似银行被扫找研发同事提供，参考附录定义 | string | N | 100001 |
| body | 商品描述 | string | N | XXX停车场XXX车牌停车缴费XX元 |
| payer | 付款方: 传入微信open_id，小程序支付时必须传 | string | N | XXXXXXXXXXX |
| callback_url | 支付成功返回前端页面 | string | N | https://a.b.c/backurl |
| auth_code | 扣款授权码: 微信付款码/支付宝授权码(被扫必传) | string | Y | 2012312313213123213 |
| notify_url | 后端支付回调地址 | string | Y | https://a.b.c/notify |
| expire_time | 交易失效时间, 单位ms, 未设置默认3分钟失效, 格式: yyyy-MM-dd'T'HH:mm:ss'Z' | string | N | 2021-09-02T09:36:46.020Z |
| trade_scene | 交易场景值, 根据商户交易按要求传递, 未按要求传递将无法正常支付, 参考附录定义 | string | Y | - |
| extra | 根据交易场景传递, 参考附录定义 | string | N | {"key1":"value1","key2":"value2"} |
| manual_settle | 仅聚合到账有效, 手动清算标记: 1-手动清算, 0-自动清算(默认) | int | N | 0 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |

### 请求示例（被扫示例）

**签名前字符串:**
```
1.1：签名前字符串
    str=app_id=op009619631234213&auth_code=135680596336872323&body=【川A12345】在【XXXXX-停车场】于【2021-07-01 00:00:00】支付费1.00元&merchant=62626601&notify_url=https://a.b.c/notify&pay_order=11111111111&payer=o1kTSt3wG76-vZh_ORgd9Ef6MXYQ&subject=停车支付3.00元(川A12345)&value=10&app_secret=6409292d666251341234213

1.2：MD5(str)
    sign=8E0050337ACD79D9FFA44CFCFD6A2919
```

**Java代码示例:**
```java
@Test
public void testPaymentCreateAuthCode(){
    TreeMap<String, String> map = new TreeMap<>();
    // app_id平台分配
    map.put("app_id", "op009619631234213");
    // 停车场商户号
    map.put("merchant", "62626601");
    // 发起支付订单号同一个app_id下唯一不能重复
    map.put("pay_order", "11111111111");
    // 商品名称
    map.put("subject","停车支付3.00元(川A12345)");
    // 商品描述
    map.put("body","【川A12345】在【XXXXX-停车场】于【2021-07-01 00:00:00】支付费0.10元");
    // 支付金额, 单位分
    map.put("value","10");
    // 扫码得到的值
    map.put("auth_code", "135680596336872323");
    // 后端支付回调地址
    map.put("notify_url", "https://a.b.c/notify");

    StringBuilder builder = new StringBuilder();
    for (String key : map.keySet()) {
        builder.append(key + "=" + map.get(key) + "&");
    }
    String appSecret = "6409292d666251341234213";
    String encriptStr = builder.toString() + "app_secret=" + appSecret;
    System.out.println(encriptStr);
    String sign = MD5.encryptHEX(encriptStr);
    String keyStr = builder.toString() + "sign=" + sign;
    System.out.println(keyStr);

    try {
        Form form = Form.form();
        for (String key : map.keySet()) {
            form.add(key, map.get(key));
        }
        form.add("sign", sign);
        Response response = Request.Post("https://papi.4pyun.com/gate/1.0/payment/trade/create")
            .bodyForm(form.build(), Charset.forName("utf-8"))
            .execute();
        HttpResponse response1 = response.returnResponse();
        System.out.println(response1.getStatusLine());
        String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
        System.out.println(text);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

### 请求返回结果参数说明

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|---------|---------|------|------|------|
| code | 请求状态码 | string | Y | 1000-交易已受理(待支付) 1001-扣款成功 1403-支付订单号已存在 其它-读取message |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到查问题尽量提供这个值 |
| pay_serial | 支付平台订单号，成功调用必返回 | string | N | 20210712215150075521111111 |

### 请求返回结果示例

**被扫正常返回（扣款已经受理）:**
```json
{
    "code": "1000",
    "seqno": "9ce2f9ac8816496d",
    "data_node": "CN-South/HS3-2",
    "trade": "",
    "pay_serial": "20210712215150075521111111"
}
```

**被扫扣款成功:**
```json
{
    "code": "1001",
    "seqno": "9ce2f9ac8816496d",
    "data_node": "CN-South/HS3-2",
    "trade": "",
    "pay_serial": "20210712215150075521111111"
}
```

**参数错误:**
```json
{
    "code": "400",
    "message": "请求参数错误",
    "hint": "`merchant` Required!",
    "seqno": "94929a9b0874aa46",
    "data_node": "CN-South/HS3-2",
    "path": "POST /gate/1.0/payment/trade/create"
}
```

**扣款错误:**
```json
{
    "code": "500",
    "message": "订单号已存在",
    "seqno": "93128ddd750dc90e",
    "data_node": "CN-South/HS3-3",
    "path": "POST /gate/1.0/payment/trade/create"
}
```

---

## 2. 发起交易预请求

**接口URL:**
```
POST https://papi.4pyun.com/gate/1.0/payment/trade/prepare
```

**接口说明:**
```
1. app_id,app_secret 用户身份id和加密密钥由平台方提供，对接方需提供公司全称然后给到商务提交给研发申请
2. 无感支付场景必须传递extra具体参考测试用例
3. deduct_mode是无感状态同步接口返回的值，该值平台不关注具体值是多少,对接方也无需关心，微信或者支付宝透传给平台，平台再透传给调用方调用方再透传给到微信或者支付宝
4. 该接口必须后端发起请求不能直接在前端调用该接口
5. 历史小程序插件(新对接已经不再支持)`callback_url`在小程序调用场景，仅支持当前小程序内页面跳转，需传入例如`/pages/index/index`！
6. 半屏小程序`callback_url`在小程序调用场景不需要传递 页面回调参考半屏小程序部分文档说明
```

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|---------|---------|------|------|------|
| app_id | 平台分配的接入应用ID | string | Y | op1234567723122 |
| merchant | 停车场商户号 | string | Y | 6262666666 |
| pay_order | 发起支付订单号，同一个app_id下唯一不能重复 | string | Y | PAYORDER-XXXXXXXX |
| subject | 商品名称 | string | Y | 支付XX项目 |
| value | 支付金额, 单位分 | string | Y | 100 |
| body | 商品描述 | string | N | XXX停车场XXX车牌停车缴费XX元 |
| payer | 付款方: 传入微信/支付宝/其他openid | string | N | XXXXXXXXXXX |
| deduct_mode | 扣款模式: 车主服务传入(透传无感同步接口4.4返回的值) | string | N | PROACTIVE |
| callback_url | 支付成功返回前端页面 | string | N | https://a.b.c/backurl |
| notify_url | 后端支付回调地址 | string | Y | https://a.b.c/notify |
| expire_time | 交易失效时间, 未设置默认3分钟失效, 格式: yyyy-MM-dd'T'HH:mm:ss'Z'。特别说明UTC时间和普通时间差8小时，因为我们在东八区，2022-09-01T00:00:00.000Z对应时间的时间是2022-09-01 08:00:00 | string | N | 2021-09-02T09:36:46.020Z |
| trade_scene | 交易场景值, 根据商户交易按要求传递, 未按要求传递将无法正常支付, 参考附录定义 | string | Y | - |
| trade_item | 交易事项, 适用于部分例如充电项目区分服务费和电费, 传递JSON字符串 | string | N | [{"key":"energy_value","value":2103},{"key":"service_value","value":100}] |
| extra | 根据交易场景传递, 参考附录定义 | string | Y | {"key1":"value1","key2":"value2"} |
| manual_settle | 仅聚合到账有效, 手动清算标记: 1-手动清算, 0-自动清算(默认) | int | N | 0 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |

### 交易事项参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|---------|---------|------|------|------|
| key | 交易事项标识, 跟随交易场景设置, 参考附录定义 | string | Y | energy_value |
| value | 交易金额, 单位: 分 | int | Y | 10 |

### 请求示例

**签名前字符串:**
```
1.1：签名前字符串
  str=app_id=op0096196358123123&body=【川A12345】在【XXXXX-停车场】于【2021-07-01 00:00:00】支付费1.00元&callback_url=https://a.b.c/backurl&channel=200201&extra={"plate_color":"-1","parking_serial":"01009970545516206225805859659","plate":"川AA37K2","enter_time":"1620621689000","park_name":"P云支付体验-停车场","parking_time":"175"}&merchant=62626601&notify_url=https://a.b.c/notify&pay_order=11111111118&subject=停车支付3.00元(川A12345)&value=10&app_secret=6409292d66625a2a12342134

1.2：MD5(str)
  sign=96FD052F81BBB3C25B5BD628E189A154
```

**Java代码示例:**
```java
@Test
public void testPaymentPrepare(){
    TreeMap<String, String> map = new TreeMap<>();
    // app_id平台分配
    map.put("app_id", "op0096196358123123");
    // 停车场商户号
    map.put("merchant", "62626601");
    // 发起支付订单号同一个app_id下唯一不能重复
    map.put("pay_order", "11111111118");
    // 商品名称
    map.put("subject","停车支付3.00元(川A12345)");
    // 商品描述
    map.put("body","【川A12345】在【XXXXX-停车场】于【2021-07-01 00:00:00】支付费1.00元");
    // 支付金额, 单位分
    map.put("value","10");
    // 支付成功返回前端页面
    map.put("callback_url", "https://a.b.c/backurl");
    // 后端支付回调地址
    map.put("notify_url", "https://a.b.c/notify");

    Map<String, String> extraMap = new HashMap<>();
    extraMap.put("plate", "川AA37K2");
    extraMap.put("plate_color", "-1");
    extraMap.put("parking_serial", "01009970545516206225805859659");
    extraMap.put("park_name", "P云支付体验-停车场");
    extraMap.put("enter_time", "1620621689000");
    extraMap.put("parking_time", "175");
    map.put("extra", JSON.toJSONString(extraMap));

    StringBuilder builder = new StringBuilder();
    for (String key : map.keySet()) {
        builder.append(key + "=" + map.get(key) + "&");
    }
    String appSecret = "6409292d66625a2a0912acfc61ed956c";
    String encriptStr = builder.toString() + "app_secret=" + appSecret;
    System.out.println(encriptStr);
    String sign = MD5.encryptHEX(encriptStr);
    String keyStr = builder.toString() + "sign=" + sign;
    System.out.println(keyStr);

    try {
        Form form = Form.form();
        for (String key : map.keySet()) {
            form.add(key, map.get(key));
        }
        form.add("sign", sign);
        Response response = Request.Post("https://papi.4pyun.com/gate/1.0/payment/trade/prepare")
            .bodyForm(form.build(), Charset.forName("utf-8"))
            .execute();
        HttpResponse response1 = response.returnResponse();
        System.out.println(response1.getStatusLine());
        String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
        System.out.println(text);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

---

## 3. 查询交易状态

### 3.1 请求地址

```
https://papi.4pyun.com/gate/1.0/payment/trade/query
```

### 3.2 请求方式

```
HTTP GET
```

### 3.3 特殊说明

```
1: app_id,app_secret 用户身份id和加密密钥由平台方提供，对接方需提供公司全称然后给到商务提交给研发申请
2: pay_serial,pay_order两个参数至少传一个
```

### 3.4 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|---------|---------|------|------|------|
| app_id | 平台分配的接入应用ID | string | Y | op1234567723122 |
| merchant | 停车场商户号 | string | Y | 6262666666 |
| pay_order | 支付订单号 | string | N | PAYORDER-XXXXXXXX |
| pay_serial | 平台支付流水 | string | N | PAYSERIAL-XXXXXXXX |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |

### 3.5 请求示例

**签名前字符串:**
```
1.1：签名前字符串
    str=app_id=op009619634544&merchant=62626666666&pay_order=00202101010000060755912770312323&app_secret=6409292d66625a2a0912ac345345

1.2：MD5(str)
    sign=8FFDCC0A8C51BB92F183CAE2DD0C70D6
```

**Java代码示例:**
```java
@Test
public void testPaymentQuery1(){
    TreeMap<String, String> map = new TreeMap<>();
    // app_id平台分配
    map.put("app_id", "op009619634544");
    // 停车场商户号
    map.put("merchant", "62626666666");
    // 发起支付订单号同一个app_id下唯一不能重复
    map.put("pay_order", "00202101010000060755912770312323");

    StringBuilder builder = new StringBuilder();
    for (String key : map.keySet()) {
        builder.append(key + "=" + map.get(key) + "&");
    }
    String appSecret = "6409292d66625a2a0912ac345345";
    String encriptStr = builder.toString() + "app_secret=" + appSecret;
    System.out.println(encriptStr);
    String sign = MD5.encryptHEX(encriptStr);
    String keyStr = builder.toString() + "sign=" + sign;
    System.out.println(keyStr);

    try {
        Form form = Form.form();
        for (String key : map.keySet()) {
            form.add(key, map.get(key));
        }
        form.add("sign", sign);
        Response response = Request.Get("https://papi.4pyun.com/gate/1.0/payment/trade/query?"+keyStr)
            .execute();
        HttpResponse response1 = response.returnResponse();
        System.out.println(response1.getStatusLine());
        String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
        System.out.println(text);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

### 3.6 请求返回结果参数说明

**基础响应参数:**

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|---------|---------|------|------|------|
| code | 请求状态码 | string | Y | 1001:查询成功(不代表支付结果) 400:参数错误 其它状态码:读取message |
| message | 返回描述 | string | N | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到查问题尽量提供这个值 |
| merchant | 停车场商户号 | string | Y | 6262666666 |
| pay_order | 支付订单号 | string | N | 20210712215150075521111111 |
| channel | 微信:200201 支付宝:100102 其它找研发同事提供 | string | N | 100001 |
| pay_serial | 平台支付流水 | string | N | PAYSERIAL-XXXXXXXX |
| value | 支付金额, 单位分 | string | N | 100 |
| status | 交易状态: 1支付成功、-1失败、0支付中 | string | N | 1 |
| trade_time | 支付完成时间 | string | N | 2020-12-31T16:00:15Z |
| payer | 微信/支付宝/其他openid | string | N | XXXXXXXXXXX |
| refund_list | 退款记录 | List[Refund] | N | Refund |

**Refund 退款记录参数:**

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|---------|---------|------|------|------|
| type | 退款类型 | int | Y | 0 普通 1 清算 |
| refund_order | 退款订单号 | string | Y | - |
| refund_serial | 第三方退款流水 | string | Y | - |
| value | 退款金额，单位分 | int | Y | - |
| process | 退款进度: 0 退款中, 1 退款成功, -1 退款失败（只会返回成功记录1） | int | Y | - |
| reason | 退款原因 | string | N | - |
| refund_time | 退款完成时间 | string | Y | - |

### 3.7 请求返回结果示例

**成功返回:**
```json
{
    "code": "1001",
    "seqno": "",
    "data_node": "",
    "time_cost": 0,
    "payload": {
        "pay_order": "",
        "channel": "",
        "pay_serial": "",
        "value": 0,
        "status": 0,
        "trade_time": "",
        "refund_list": []
    }
}
```

---

## 4. 发起订单退款

### 4.1 请求地址

```
https://papi.4pyun.com/gate/1.0/payment/trade/refund
```

### 4.2 调用方式

```
HTTP POST application/json
```

### 4.3 特殊说明

```
1: app_id,app_secret 应用id和加密密钥由平台方提供，对接方需提供公司全称然后给到商务提交给研发申请
2: 该退款接口只允许对已授权商户的订单发起退款，请联系研发进行授权配置
```

### 4.4 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|---------|---------|------|------|------|
| app_id | 平台分配的接入应用ID | string | Y | op1234567723122 |
| order | 退款请求单号 | string | N | 20220217161036075521110362 |
| pay_serial | 平台支付流水 | string | Y | 20220217161036075521110362 |
| value | 退款金额(单位分，大于0, 小于等于订单金额) | string | Y | 1 |
| reason | 退款原因 | string | N | 客户要求退款 |

### 4.5 请求示例

**签名算法:**

JSON请求报文拼接"&app_secret=${app_secret}"：
```
1.1：签名前字符串
    {"reason":"接口测试退款","pay_serial":"20220721102644066066610031","app_id":"op00961963581daa7","value":"1"}&app_secret=6409292d66625a2a0912acfc61ed956c

1.2：MD5(str)
    sign=55D9BC675B3B042A015895FA9F9D037B
```

**Java代码示例:**
```java
@Test
public void testRefundCreate() {
    String url = "https://dev-api.4pyun.com/gate/1.0/payment/refund/create";
    String appId = "op00961963581daa7";
    String appSecret = "6409292d66625a2a0912acfc61ed956c";

    Map<String, String> paramMap = Maps.newHashMap();
    // 应用ID
    paramMap.put("app_id", appId);
    // 退款请求单号
    paramMap.put("order", "R2024032114351106991");
    // 平台支付订单号
    paramMap.put("pay_serial", "20220721143511066066610042");
    // 退款金额
    paramMap.put("value", "1");
    // 退款原因
    paramMap.put("reason", "接口测试退款");

    // 转成Json参数格式
    String jsonStr = JSON.toJSONString(paramMap);
    // 生成待签名参数
    String encryptStr = jsonStr + "&app_secret=" + appSecret;
    System.out.println("encryptStr: " + encryptStr);
    String sign = MD5.encryptHEX(encryptStr);
    System.out.println("sign: " + sign);

    try {
        System.out.println("Request: " + jsonStr);
        Response response = Request.Post(url)
            // 签名设置在请求头，header-name为"Authorization"
            .setHeader("Authorization", sign)
            // 请求参数格式为application/json;charset=utf-8
            .bodyString(jsonStr, ContentType.APPLICATION_JSON.withCharset(CharsetUtils.UTF_8))
            .execute();
        String responseBody = EntityUtils.toString(response.returnResponse().getEntity());
        System.out.println("Response: " + responseBody);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

### 4.6 请求返回结果参数说明

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|---------|---------|------|------|------|
| code | 请求状态码 | string | Y | 1001:退款成功 400:参数错误 1403:没有该订单退款权限 1003:可用退款余额不足 1405:退款失败 其它状态码:读取message |
| message | 返回描述 | string | N | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到查问题尽量提供这个值 |
| pay_serial | 平台支付流水 | string | N | 20220719163604066066610014 |
| refund_order | 平台退款订单号 | string | N | 20220725004608075527054 |
| refund_serial | 第三方退款流水 | String | N | 50301802862022072523029437751 |
| refund_time | 退款时间 | String | N | 2022-02-17T08:12:46.616Z |

### 4.7 请求返回结果示例

**成功返回:**
```json
{
    "code": "1001",
    "seqno": "9937485877757468",
    "data_node": "CN-South/DEV-1",
    "time_cost": 2854,
    "payload": {
        "pay_serial": "20220719163604066066610014",
        "trade": "",
        "refund_order": "20220725004608075527054",
        "refund_serial": "50301802862022072523029437751",
        "refund_time": "2022-07-24T16:46:09.755Z",
        "message": "",
        "extra": {}
    }
}
```

**参数错误:**
```json
{
    "code": "400",
    "message": "请求参数错误",
    "hint": "`pay_serial` Required!",
    "seqno": "1056831907048775",
    "data_node": "CN-South/DEV-1",
    "time_cost": 76
}
```

---

## 5. 退款订单查询

### 5.1 请求地址

```
https://papi.4pyun.com/gate/1.0/payment/trade/refund
```

### 5.2 调用方式

```
HTTP GET
```

### 5.3 特殊说明

```
1: app_id,app_secret 应用id和加密密钥由平台方提供，对接方需提供公司全称然后给到商务提交给研发申请
2: 该退款查询接口只允许查询已授权商户的订单，请联系研发进行授权配置
```

### 5.4 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|---------|---------|------|------|------|
| app_id | 平台分配的接入应用ID | string | Y | op1234567723122 |
| merchant | 退款订单所属商户号 | string | Y | 62626601 |
| order | 退款请求单号，对应发起退款时传入参数 | string | Y | 20220217161036075521110362 |

### 5.5 请求示例

**签名算法:**
```
1. 设所有发送或接收到的数据为集合M，将集合M内非空参数值的参数按照参数名ASCII码从小到大排序（字典序），使用URL键值对的格式（即 key1=value1&key2=value2...）拼接成字符串 stringA。
2. sign = stringA + "&app_secret=" + appSecret，取MD5（32位不区分大小写）。

注意事项：
1. 参数名ASCII码从小到大排序（字典序）；
2. 如果参数值为空（即null）不参与签名；
3. 参数名区分大小写；
4. 验证签名时，sign参数不参与签名，将生成的签名与该sign值作校验；
5. 接口可能增加字段，验证签名时必须支持增加的扩展字段；
```

**Java代码示例:**
```java
@Test
public void testRefundQuery() {
    String url = "https://dev-api.4pyun.com/gate/1.0/payment/refund/create";
    String appId = "op00961963581daa7";
    String appSecret = "6409292d66625a2a0912acfc61ed956c";

    Map<String, String> paramMap = Maps.newTreeMap();
    // 应用ID
    paramMap.put("app_id", appId);
    // 退款订单所属商户号
    paramMap.put("merchant", "62626601");
    // 退款请求单号
    paramMap.put("order", "TEST_20240320145031953");

    // 生成待签名参数
    String str = StringUtils.join(paramMap, "&", "=") + "&app_secret=" + appSecret;
    System.out.println("encryptStr: " + str);
    // 生成签名
    String sign = MD5.encryptHEX(str);
    System.out.println("sign: " + sign);
    paramMap.put("sign", sign);

    // 组装请求URL
    String requestUrl = UrlUtils.build(url, paramMap);
    try {
        System.out.println("Request: " + requestUrl);
        HttpResponse httpResponse = Request.Get(requestUrl).execute().returnResponse();
        String responseBody = EntityUtils.toString(httpResponse.getEntity());
        System.out.println("Response: " + responseBody);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

### 5.6 请求返回结果参数说明

**基础响应参数:**

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|---------|---------|------|------|------|
| code | 请求状态码 | string | Y | 1001:查询成功 1002:退款订单不存在 1400:参数错误 1403:应用权限错误 其它状态码:读取message |
| message | 返回描述 | string | N | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到查问题尽量提供这个值 |
| payload | 退款订单信息 | json | N | 详见下方payload字段说明 |

**payload 退款订单信息参数:**

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|---------|---------|------|------|------|
| merchant | 商户号 | string | Y | - |
| order | 退款请求单号 | string | Y | - |
| refund_order | 退款订单号 | string | Y | - |
| refund_serial | 退款流水 | string | N | - |
| reason | 退款原因 | string | N | - |
| receipt_url | 退款凭证 | string | N | - |
| pay_serial | 原支付流水 | string | Y | - |
| value | 退款金额(单位分) | int | Y | - |
| process | 退款进度: 0 退款中, 1 退款成功, -1 退款失败 | string | Y | - |
| create_time | 创建时间 | string | Y | - |
| refund_time | 退款时间 | string | N | 格式如：2022-02-17T08:12:46.616Z |
| operator_id | 退款操作员ID | string | Y | - |
| operator_name | 退款操作员名称 | string | Y | - |

### 5.7 请求返回结果示例

**查询成功:**
```json
{
    "code": "1001",
    "seqno": "60661674882214038340794721836706",
    "data_node": "CN-South/DEV-3",
    "time_cost": 14,
    "payload": {
        "order": "TEST_20240321165705440",
        "refund_order": "20240321165706075524320",
        "refund_serial": "1770736381283725312",
        "pay_serial": "20240321165625066020110009",
        "value": 1,
        "process": 1,
        "create_time": "2024-03-21T08:57:06Z",
        "refund_time": "2024-03-21T08:57:08Z",
        "operator_id": "op94450a8603f8843",
        "operator_name": "测试应用",
        "reason": "测试退款",
        "merchant": "97919537",
        "receipt_url": ""
    }
}
```

**参数错误:**
```json
{
    "code": "1400",
    "message": "`order` is required",
    "seqno": "28610145597796892868994206094424",
    "data_node": "CN-South/DEV-3",
    "time_cost": 116,
    "payload": {
        "order": "",
        "refund_order": "",
        "refund_serial": "",
        "pay_serial": "",
        "value": 0,
        "process": 0,
        "operator_id": "",
        "operator_name": "",
        "reason": "",
        "merchant": "",
        "receipt_url": ""
    }
}
```

**退款订单不存在:**
```json
{
    "code": "1002",
    "message": "退款订单不存在",
    "hint": "merchant=97919537, order=TEST_20240321165705441",
    "seqno": "41233945733378583301176401889043",
    "data_node": "CN-South/DEV-3",
    "time_cost": 6,
    "payload": {
        "order": "",
        "refund_order": "",
        "refund_serial": "",
        "pay_serial": "",
        "value": 0,
        "process": 0,
        "operator_id": "",
        "operator_name": "",
        "reason": "",
        "merchant": "",
        "receipt_url": ""
    }
}
```

**没有该订单退款权限:**
```json
{
    "code": "1403",
    "message": "该商户未授权该应用",
    "hint": "app_id=op94450a8603f8843, merchant=17627004",
    "seqno": "91250735701538035211017554555339",
    "data_node": "CN-South/DEV-3",
    "time_cost": 603,
    "payload": {
        "order": "",
        "refund_order": "",
        "refund_serial": "",
        "pay_serial": "",
        "value": 0,
        "process": 0,
        "operator_id": "",
        "operator_name": "",
        "reason": "",
        "merchant": "",
        "receipt_url": ""
    }
}
```

---

## 6. 支付结果异步通知

### 6.1 通知地址

**通知地址来源:**
- 发起交易请求(微信/支付宝内H5)
- 发起交易请求(小程序/被扫)

**说明:**
```
上面两个接口中请求参数中的notify_url
```

**描述:**
```
合作方实现该接口用于接受支付结果通知, 若通知未能返回1001不成功则会重复通知多次。
```

### 6.2 请求方式

```
HTTP POST FORM 表单提交
```

### 6.3 特殊说明

```
1: 该接口是回调由对接方实现平台主动调用该接口
2: pay_order是 1)发起交易请求(微信/支付宝内H5) / 2)发起交易请求(小程序/被扫) 中参数pay_order字段
3: 如果已经处理返回成功了平台重复通知的情况直接返回1001 这样平台就不会再次通知了
```

### 6.4 请求参数（平台推送参数）

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 平台分配的接入应用ID |
| merchant | string | Y | 商户号 |
| pay_order | string | Y | 支付订单号 |
| channel | string | Y | 支付渠道 |
| pay_serial | string | Y | 平台支付流水 |
| trade_no | string | Y | 支付渠道(微信/支付宝)交易单号 |
| value | string | Y | 支付金额，单位：分 |
| status | short | Y | 交易状态: 1支付成功、-1失败、0支付中 |
| trade_time | long | Y | 交易时间, 单位ms |
| pay_mode | short | Y | 到账方式具体参考pay_mode_desc描述 |
| pay_mode_desc | string | Y | 到账方式描述, 例如: 微信、支付宝 |
| fee | int | Y | 交易手续费, 单位分 |
| payer | string | N | 付款用户的微信/支付宝的openid |
| sign | string | Y | 请求数据签名 |

### 6.5 响应参数（对接方返回参数）

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 业务处理状态码 1001:通知成功 其它状态码:读取message |
| message | string | N | 业务处理状态说明 |
| hint | string | N | 提示说明 |

### 6.6 请求返回结果示例

**成功返回:**
```json
{
    "code": "1001",
    "message": "通知成功",
    "hint": ""
}
```

---

## 附录

### 签名规则

根据HTTP请求 **提交数据方式** 的不同而有所差异。

#### URL传参/Form表单传参签名规则

1. 先将所有业务参数按照参数名（不包括 sign）进行升序排序（若有多个相同参数名则继续按照其参数值进行升序排序）;
2. 以 'http url' 参数风格拼接成待签名的字符串，如：`a=v&b=0&c=1900000109&d=102`
3. 再将密钥拼接到待签名的字符串后面，如：`a=v&b=0&c=1900000109&d=102&app_secret=XXXXX`
4. 使用标准MD5算法对待签名字符串进行签名;
5. 将签名值写入 'sign' 字段;

**URL传参请求示例代码:**
```java
String appId = "opxxxxx";
String appSecret = "XXXXX";

Map<String, String> params = new TreeMap<>();
params.put("app_id", appId);
params.put("park_uuid", "e24deadf-1aa0-4981-bde5-f9c474c4f5f5");

StringBuilder builder = new StringBuilder();
for (String key : params.keySet()) {
    builder.append(key + "=" + map.get(key) + "&");
}

String encryptStr = builder.toString() + "app_secret=" + appSecret;
String sign = MD5.encryptHEX(encryptStr);

String paramStr = builder.toString() + "sign=" + sign;
String url = "https://api.4pyun.com/gate/1.0/xxxxx?" + paramStr;

Response response = Request.Get(url).execute();
```

#### application/json 传参签名规则

对于通过HTTP Body以格式为 **application/json** 传参的请求，签名规则如下：

1. 将请求JSON报文整体作为字符串;
2. 在JSON字符串后面拼接 `&app_secret=${app_secret}`;
3. 使用标准MD5算法对整体进行签名;
4. 签名值放在请求头的 `Authorization` 字段中;

**JSON传参请求示例代码:**
```java
Map<String, String> paramMap = Maps.newHashMap();
paramMap.put("app_id", appId);
paramMap.put("pay_serial", "20220721143511066066610042");
paramMap.put("value", "1");

// 转成Json参数格式
String jsonStr = JSON.toJSONString(paramMap);
// 生成待签名参数
String encryptStr = jsonStr + "&app_secret=" + appSecret;
String sign = MD5.encryptHEX(encryptStr);

Response response = Request.Post(url)
    .setHeader("Authorization", sign)
    .bodyString(jsonStr, ContentType.APPLICATION_JSON.withCharset(CharsetUtils.UTF_8))
    .execute();
```

---

> **文档说明:** 本文档内容由P云开放平台官方文档抓取整理，原始文档地址为 https://doc.4pyun.com/openapi/
> 
> **更新时间:** 2026/07/08


---

# 其他支付平台接口

> 包括结算记录同步、结算账户查询、支付渠道配置查询、交易清算、分账交易同步、分润交易同步等。

## 1. 结算记录同步接口

**原文链接**: https://doc.4pyun.com/openapi/api/settlement-sync.html

### 1.1 通知地址

合作方提给一个通知地址并实现该接口用于接收结算记录同步通知。

- 该接口是回调由对接方实现平台主动调用该接口
- 若通知未能返回 `1001`（处理不成功），则平台会重复通知多次

### 1.2 请求方式

```
POST JSON
```

### 1.3 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 平台分配的接入应用ID |
| sign | string | Y | 请求数据签名 |
| merchant | string | Y | 商户号 |
| serial | string | Y | 结算流水，全局唯一 |
| subject | string | Y | 结算主题 |
| create_time | long | Y | 结算记录创建时间，单位ms |
| start_time | long | Y | 结算记录开始时间，单位ms |
| end_time | long | Y | 结算记录结束时间，单位ms |
| trade_count | long | Y | 交易笔数 |
| total_value | long | Y | 总交易金额（分） |
| settle_value | long | Y | 最终结算金额（分） |
| service_value | int | Y | 手续费（分） |
| status | short | Y | 结算状态：1 已清算；0 未清算 |
| type | short | Y | 结算类型：1正常，2补款，-1扣款，-2退款 |
| transfer_status | short | Y | 划账状态：0未划账；1已划账；-1划账异常 |
| trade_list | list(Payment) | N | 关联支付记录，仅在未划账会携带该字段，后续同步划账状态不再携带该字段 |

**支付记录（Payment）**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| pay_serial | String | Y | 平台支付流水 |
| pay_order | String | Y | 第三方支付流水 |
| value | int | Y | 支付金额（分） |
| fee | int | Y | 手续费（分） |
| refund_value | int | Y | 退款金额（分），大于0表示有退款 |

### 1.4 响应参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 业务处理状态码<br>1001:通知成功<br>其它状态码:读取message |
| message | string | N | 业务处理状态说明 |
| hint | string | N | 提示说明 |

**示例:**

```json
{
    "code": "1001",
    "message": "通知成功",
    "hint": ""
}
```

---

## 2. 结算帐户信息查询接口

**原文链接**: https://doc.4pyun.com/openapi/api/bank-account.html

### 2.1 接口URL

```
GET https://api.4pyun.com/gate/1.0/payment/channel/bank/account
```

### 2.2 接口说明

该接口通过P云平台商户号查询该商户对应的结算帐户信息。

### 2.3 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 平台分配的接入应用ID |
| sign | string | Y | 请求数据签名 |
| merchant | string | Y | 商户号 |

### 2.4 公共响应参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 业务处理状态码<br>1001:查询成功<br>1002:结算帐户不存在<br>其它状态码:读取message |
| message | string | N | 业务处理状态说明 |
| hint | string | N | 提示说明 |
| seqno | string | Y | 请求编号，用于排查定位请求问题 |
| data_node | string | Y | 请求服务器编码 |
| time_cost | long | Y | 请求响应耗时，单位毫秒 |
| payload | object | N | 响应业务参数 |

### 2.5 业务响应参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| merchant | string | Y | 商户号 |
| merchant_name | string | Y | 商户名称 |
| payee_name | string | Y | 收款帐号主体名称 |
| payee_account | string | Y | 收款帐号 |
| payee_type | string | Y | 收款帐号类型 |
| bank_name | string | Y | 银行名称 |
| bank_province | string | Y | 支行所属省份 |
| bank_city | string | Y | 支行所属城市 |
| bank_branch | string | Y | 支行名称 |
| status | short | Y | 结算帐户状态：1 帐户正常；0 帐户异常 |
| status_desc | string | N | 帐户状态描述，帐户异常则为异常原因描述 |
| bank_code | string | Y | 支行联行号 |

### 2.6 请求返回结果示例

```json
{
    "code": "1001",
    "seqno": "00588928118416202531675723189402",
    "data_node": "CN-South/DEV-1",
    "time_cost": 846,
    "payload": {
        "merchant": "62626601",
        "payee_name": "永泰县XX总公司",
        "payee_account": "1402080109814603875",
        "payee_type": "EnterpriseBankAccount",
        "bank_name": "中国工商银行",
        "bank_province": "上海市",
        "bank_city": "上海市",
        "bank_branch": "中国工商银行股份有限公司上海市永泰路支行",
        "id": "703279940799303681",
        "status": 0,
        "status_desc": "户名有误",
        "subject_template": "P云支付体验${day}日${type}",
        "bank_code": "102290006474",
        "withdraw_type": 0,
        "settle_cycle": "W1",
        "min_transfer_value": 5000,
        "priority": 5,
        "abnormal_type": 1,
        "merchant_name": "P云支付体验-停车场"
    }
}
```

---

## 3. 支付渠道配置信息查询

**原文链接**: https://doc.4pyun.com/openapi/api/channel-config.html

### 3.1 接口URL

```
GET https://api.4pyun.com/gate/1.0/payment/channel/config
```

### 3.2 接口说明

该接口通过P云平台商户号查询该商户对应的支付渠道配置信息。

### 3.3 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 平台分配的接入应用ID |
| sign | string | Y | 请求数据签名 |
| merchant | string | Y | 商户号 |

### 3.4 公共响应参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 业务处理状态码<br>1001:查询成功<br>1002:结算帐户不存在<br>其它状态码:读取message |
| message | string | N | 业务处理状态说明 |
| hint | string | N | 提示说明 |
| seqno | string | Y | 请求编号，用于排查定位请求问题 |
| data_node | string | Y | 请求服务器编码 |
| time_cost | long | Y | 请求响应耗时，单位毫秒 |
| payload | object | N | 响应业务参数 |

### 3.5 业务响应参数

| payload字段 | 类型 | 必须 | 说明 |
|-------------|------|------|------|
| row | list | Y | 支付渠道配置列表 |

**row字段**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| type_id | short | N | 支付渠道类型ID |
| name | string | N | 支付渠道类型名称 |
| status | short | N | 支付渠道配置状态：0未配置；1待审核；2审核中；5代验证；4待签约；3审核通过 |
| apply_no | string | N | 支付渠道申请编号 |
| apply_sign_url | string | N | 签约链接 |
| abnormal | short | N | 是否帐户异常：0正常；1异常 |
| category_id | short | Y | 支付渠道分类ID |
| category_name | string | Y | 支付渠道分类名称 |
| type_code | string | N | 支付渠道类型编码 |
| create_time | string | N | 支付渠道申请时间 |
| update_time | string | N | 支付渠道更新时间 |
| status_desc | string | N | 异常状态描述 |

### 3.6 请求返回结果示例

```json
{
    "code": "1001",
    "seqno": "83980312925941224530392031515874",
    "data_node": "CN-South/DEV-1",
    "time_cost": 34,
    "payload": {
        "row": [
            {
                "type_id": 0,
                "name": "",
                "status": 0,
                "apply_no": "",
                "apply_sign_url": "",
                "abnormal": 0,
                "category_id": "1",
                "category_name": "微信直连",
                "type_code": "",
                "create_time": "1970-01-01T00:00:00Z",
                "abnormal_type": 0,
                "update_time": "1970-01-01T00:00:00Z",
                "status_desc": ""
            },
            {
                "type_id": 0,
                "name": "",
                "status": 0,
                "apply_no": "",
                "apply_sign_url": "",
                "abnormal": 0,
                "category_id": "2",
                "category_name": "支付宝直连",
                "type_code": "",
                "create_time": "1970-01-01T00:00:00Z",
                "abnormal_type": 0,
                "update_time": "1970-01-01T00:00:00Z",
                "status_desc": ""
            },
            {
                "type_id": 5,
                "name": "聚合支付",
                "status": 3,
                "apply_no": "C202304140633",
                "apply_sign_url": "https://dev-app.4pyun.com/contract/prepare?contract_no=DEV-P061202211160001",
                "abnormal": 1,
                "category_id": "3",
                "category_name": "聚合支付",
                "type_code": "PPPAY",
                "create_time": "2023-04-14T08:18:49Z",
                "abnormal_type": 1,
                "update_time": "2023-06-15T13:25:33Z",
                "status_desc": "户名有误"
            },
            {
                "type_id": 23,
                "name": "中国邮政支付",
                "status": -3,
                "apply_no": "C202208184494",
                "apply_sign_url": "",
                "abnormal": 0,
                "category_id": "4",
                "category_name": "银行聚合支付",
                "type_code": "",
                "create_time": "2022-08-18T08:00:01Z",
                "abnormal_type": 0,
                "update_time": "2023-04-06T03:43:00Z",
                "status_desc": "已停用"
            }
        ]
    }
}
```

---

## 4. 触发交易清算

**原文链接**: https://doc.4pyun.com/openapi/api/trade-settle.html

### 4.1 接口URL

```
POST https://api.4pyun.com/gate/1.0/payment/trade/settle
```

### 4.2 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | P云平台分配的接入应用ID | string | Y | op1234567723122 |
| pay_serial | P云支付流水（交易预请求返回） | string | Y | 20240614175507066020110395 |
| settle_value | 实际清算金额，分，需<=实际交易金额 | int | Y | 1 |
| sharding_value | 订单可分账金额，分（传递实际清算金额即可） | int | Y | 1 |
| trade_item | 交易事项，适用于部分例如充电项目区分服务费和电费，传递JSON字符串 | string | N | `[{"key":"energy_value","value":2103},{"key":"service_value","value":100}]` |
| reason | 清算原因说明 | string | Y | 充电结束退款剩余金额 |
| sign | 签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |

### 4.3 返回参数

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 状态码 | string | Y | 1001-下单成功<br>1403-支付订单号已存在<br>其它-读取message |
| message | 返回描述 | string | N | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到查问题尽量提供这个值 |

### 4.4 状态码

| 值 | 描述 |
|----|------|
| 1001 | 成功 |
| 1403 | 不可清算，参考message |
| 其它 | 参考message |

---

## 5. 分账交易同步

**原文链接**: https://doc.4pyun.com/openapi/api/sharding-trade-sync.html

### 5.1 通知地址

合作方提给一个通知地址并实现该接口用于接收分账记录同步请求。

- 该接口是回调由对接方实现平台主动调用该接口
- 若通知未能返回 `1001`（处理不成功），则平台会重复通知多次

### 5.2 请求方式

```
POST JSON
```

### 5.3 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 平台分配的接入应用ID |
| sign | string | Y | 请求数据签名 |
| merchant | object | Y | 交易商户 |
| - code | string | Y | 交易商户编号 |
| - name | string | Y | 交易商户名称 |
| partner | object | Y | 分账商户 |
| - code | string | Y | 分账商户编号 |
| - name | string | Y | 分账商户名称 |
| trade_scene | string | Y | 交易场景标识 |
| trade_no | string | Y | 分账交易单号 |
| sharding_type | short | Y | 分账类型：0-交易比例，1-定期扣款 |
| trade_type | short | Y | 分账交易类型：1-收入，-1-扣款 |
| status | short | Y | 分账记录状态：1-有效，0-无效 |
| sharding_value | int | Y | 原交易可分账金额，单位：分 |
| trade_value | int | Y | 分账金额，单位：分 |
| rule_value | int | Y | 规则分账金额/分账比例，若按交易事项分账该字段无效 |
| trade_time | date | Y | 交易时间，格式 `yyyyMMddHHmmss` |
| trade_item | list | N | 分账交易事项 |
| - key | string | Y | 交易事项标识 |
| - rate | int | Y | 分账比例，千分之X |
| - value | int | Y | 分账金额，单位：分 |

**示例:**

```json
{
    "app_id": "op00961963581daa7",
    "rule_value": 0,
    "sharding_type": 0,
    "timestamp": 1731973767578,
    "trade_time": "20241118191701",
    "merchant": {
        "name": "P云支付体验-停车场",
        "code": "62626601"
    },
    "partner": {
        "name": "测试渠道商1019MIX-J",
        "code": "87105025"
    },
    "trade_item": [
        {
            "key": "energy_value",
            "value": -50,
            "rate": 500
        },
        {
            "key": "service_value",
            "value": -13,
            "rate": 500
        }
    ],
    "trade_no": "20241118191500066020111624",
    "trade_value": -63,
    "sharding_value": 124,
    "trade_type": -1,
    "sign_type": "MD5",
    "trade_scene": "ENERGY",
    "status": 1,
    "sign": "E6020591B98425D5D412CCE0B58A1629"
}
```

### 5.4 响应参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 业务处理状态码<br>1001:通知成功<br>其它状态码:读取message |
| message | string | N | 业务处理状态说明 |
| hint | string | N | 提示说明 |

**示例:**

```json
{
    "code": "1001",
    "message": "通知成功",
    "hint": ""
}
```

---

## 6. 分润交易同步

**原文链接**: https://doc.4pyun.com/openapi/api/profit-trade-sync.html

### 6.1 通知地址

合作方提给一个通知地址并实现该接口用于接收分润订单记录同步请求。

- 该接口是回调由对接方实现平台主动调用该接口
- 若通知未能返回 `1001`（处理不成功），则平台会重复通知多次

### 6.2 请求方式

```
POST JSON
```

### 6.3 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 平台分配的接入应用ID |
| sign | string | Y | 请求数据签名 |
| merchant | string | Y | 被分润方商户号 |
| business | string | Y | 分润业务 |
| category | string | Y | 业务类型 |
| amount | int | Y | 返现值 |
| rate | int | Y | 返点值 |
| trade_merchant | string | Y | 订单所属商户 |
| trade_no | string | Y | 支付流水 |
| trace_id | string | Y | 全局关联订单号 |
| trade_subject | string | Y | 交易商品 |
| trade_value | int | Y | 订单金额，单位：分 |
| trade_time | date | Y | 支付时间，格式：`yyyy-MM-dd HH:mm:ss` |
| value | int | Y | 总分润金额，单位：分 |
| remain_value | int | Y | 剩余分润金额，单位：分 |
| type | short | N | 分润类型 |

**示例:**

```json
{
    "app_id": "op00961963581daa7",
    "timestamp": 1731973767578,
    "sign_type": "MD5",
    "sign": "E6020591B98425D5D412CCE0B58A1629",
    "merchant": "12345678",
    "business": "plus:member:renewal",
    "category": "LVIP",
    "amount": 0,
    "rate": 0,
    "trade_merchant": "87654321",
    "trade_no": "2026042115051866000110040",
    "trace_id": "12345678910",
    "trade_subject": "购买停车特惠包支付7.90元",
    "trade_value": "790",
    "trade_time": "2026-04-02 15:53:30",
    "value": "395",
    "remain_value": "395",
    "type": 1
}
```

### 6.4 响应参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 业务处理状态码<br>1001:通知成功<br>其它状态码:读取message |
| message | string | N | 业务处理状态说明 |
| hint | string | N | 提示说明 |

**示例:**

```json
{
    "code": "1001",
    "message": "通知成功",
    "hint": ""
}
```

---



---


# ==================== 第二篇：PP前端接口（停车场系统接入） ====================

---

# 1. 接入指引

## 11. 车场接入文档首页

**原文链接**: https://doc.4pyun.com/parking/

### 3. 对接要求

| 功能 | 功能概述 | 是否必须 |
|------|----------|----------|
| 移动支付（包括自动支付） | 获取停车费用，并完成支付流程 | Y |
| 商户优惠券 | 商家给车主下发停车优惠券；车主支付时可使用该优惠券 | Y |
| 月卡/储值卡/次卡 | 续费 | Y |
| 停车记录同步 | 可实现全国各地城市平台数据上报要求 | Y |
| 无感支付 | 可实现微信及银行无感支付 | Y |

### 3.1 移动支付

主要对接需求有: **获取停车支付订单**，**订单支付结果通知**，**同步车辆列表(下行)** 和 **设置车辆自动支付权限(下行)** 等。

| 接口 | 接口概述 | 是否必须 |
|------|----------|----------|
| 获取停车订单 | 获取停车费用，返回基本停车信息、优惠信息、已支付金额以及需支付金额 | Y |
| 订单支付结果通知 | 完成支付后，通过该接口完成订单同步 | Y |
| 停车场实时数据拉取 | 获取停场的车辆数和总车位等数据 | Y |
| 车辆自动支付权限开关 | 当车辆入场，由P云主动下发车辆享受后付费出场额度，当额度内，车辆出场直接放行；超过额度则取消本次预付费标记；P云根据出场纪录中返回的预付费实际支付额度完成用户扣款 | Y |
| 车辆出场锁定或解锁 | 用户可锁定车辆，锁定后，车辆不可离场 | N |
| 获取停车记录详情 | a. 停车信息: 出入场时间、停车时长、收费金额、优惠信息、收费员信息、出入场图片地址<br>b. 收费信息: 本地停车所有成功的收费记录，需要区分支付方式 | Y |
| 无牌车入场 | 用户扫描入场二维码后，P云会调用该接口完成入场开闸 | Y |

### 3.2 商户优惠券

大型商场都具有多种优惠，面对种类繁多的优惠类型，P云为商场提供纯电子化优惠券的整体解决方案。

| 接口 | 接口概述 | 是否必须 |
|------|----------|----------|
| 电子优惠券派发 | P云通过该接口派发电子优惠到具体某次停车 | Y |
| 电子优惠撤销 | 当车辆未出场前，电子优惠可被撤销 | Y |

### 3.3 月卡/次卡/储值卡

P云为停车场提供月卡/次卡/储值卡续费的移动支付方案。

| 接口 | 接口概述 | 是否必须 |
|------|----------|----------|
| 车辆信息查询 | 提供根据车牌/客户信息查询在当前停车场办理月卡/储值/其他卡类信息(收费等信息) | Y |
| 车辆续费通知 | 用户完成续费后通过该接口通知停车场客户续费结果；例如: 续费30天/充值100块 | Y |

---

## 12. 网络说明

**原文链接**: https://doc.4pyun.com/parking/network.html

### 车场交互网络信息

| 场景 | 地址 | 说明 |
|------|------|------|
| 指令下发 | tcp://????.gate.4pyun.com:8661 | 用于本地车场和平台数据交互（实际域名根据车场下载接入参数） |
| 数据同步 | https://api.4pyun.com/ | 用于本地推送出入场记录到平台 |
| 数据存储 | https://files.4pyun.com/ | 平台数据存储 |
| 后端接口 | https://mapi.4pyun.com/ | 商户平台后端接口服务 |
| 商户平台 | https://mch.4pyun.com/ | 用于本地访问平台管理车场数据 |

- IP请自己PING获取，可能随业务调整变化，建议绑定域名。

---

## 13. 常见问题

**原文链接**: https://doc.4pyun.com/parking/faq.html

> 该页面内容在抓取时未获取到具体内容，页面可能为空或尚未编辑。

---



---

# 2. 临停缴费接口

## 2.1 获取临停缴费订单

### 接口说明

当用户在P云场时获取停车订单时, P云将发送该协议请求到停车场系统, 通过车牌号/停车卡ID获取停车支付订单。

**服务名**: `service.parking.payment.billing`
**版本号**: `1.0`

#### 付费入场说明

当付费入场当前接口传递参数 `gate_id` 对应车场系统通道类型为入口, 厂商自行判断是否允许付费入场, 若不允许直接返回没有订单或返回错误信息即可; 反之直接返回付费入场的费用。

- 若为无牌车入场, 虚拟车牌会在支付结果同步时传递给车场系统。
- 若无牌车通道扫码没有压地感, 需要返回1500并在将原因设置到message中，不可返回1002。
- 若确认传入passport已经离场则必须返回1002状态码。

#### 时段月卡/多车共位

在多月共位/时段月卡的场景, 可返回 `car_type`, 用于当非临时车辆时(`car_type!=1`), 前端可选择跳转固定车续费或直接缴临停费用。

#### 无牌车通道计费逻辑

通道计费有以下情况：

- 如果客户有没有离场的无牌车记录，通道计费会下发 `gate_id` + `passport` (无牌车虚拟车牌)
- 没有有效无牌车记录，计费下发 `gate_id`

建议本地软件在通道计费返回订单逻辑, 根据 `gate_id` 判断当前通道车辆信息有以下情况:

1. 当前识别到有牌车，直接返回有牌车费用;
2. 当识别到无牌车，检查传入参数 `passport`，根据 `passport` 返回停车费用;
3. 当识别没有车辆，没有传入 `passport`，提示没有停车记录（result_code=1002）;
4. 当识别没有车辆，传入 `passport`，并且 `passport` 没有停车中记录，提示没有停车记录，会自动标记无牌车状态已失效(result_code=1002);
5. 当识别没有车辆，传入 `passport`，并且 `passport` 有停车中记录，提示没有检测到无牌车，防止系统自动清理无牌车状态)(result_code=1500)。

### 请求参数

#### 基础参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.payment.billing` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

#### 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号, 由P云分配 |
| plate | string | N | 支付车牌号 |
| card_id | string | N | 支付停车卡物理ID |
| passport | string | N | 用户通行证ID, 无牌车传入 |
| gate_id | string | N | 通道编号ID, 无牌车付费时可传递触发开闸 |
| charge_type | string | N | 指定收费规则, 前端直接透传到车场 |

> **注**: `card_id`, `plate` 和 `passport` 为互斥参数。若传递 `card_id` 则停车场应该根据停车卡查询停车信息返回停车订单; 若传递 `plate` 停车场应根据车牌号返回停车订单; 而 `passport` 则是P云为无牌车生成的一个唯一标识。

### 请求示例

```json
{
    "charset": "UTF-8",
    "park_uuid": "aaaaaaa-ec98-46be-89e3-26bca7be833e",
    "plate": "粤B660PP",
    "service": "service.parking.payment.billing",
    "version": "1.0"
}
```

### 应答参数

#### 基础参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.payment.billing` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| message | string | Y | 状态码处理描述, 如:返回错误信息 |
| sign | string | Y | 签名 |

#### result_code 状态码说明

| 值 | 含义 |
|----|------|
| 1001 | 订单获取成功, 业务参数将返回 |
| 1002 | 未查询到停车信息 |
| 1003 | 固定车辆, 不允许支付(有效期内返回, 过期不返回此状态) |
| 1401 | 签名错误, 请检查配置 |
| 1500 | 接口处理异常, 例如: 通道获取订单没有监测到车辆、连接数据库失败等异常 |

#### 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| plate | string | N | 识别车牌号码 |
| card_id | string | N | 无牌车获取订单返回本地的虚拟卡ID/虚拟车牌 |
| parking_serial | string | Y | 停车流水, 标识具体某次停车事件, 需保证该停车场下唯一, 付费入场可不返回。和进出场上报`parking_serial`保持一致!!! |
| parking_order | string | Y | 停车支付订单号, 需保证该停车场下唯一。注:同一停车场内不可重复！ |
| enter_time | string | Y | 入场时间, 格式 `yyyyMMddHHmmss` |
| parking_time | int | Y | 停车时长(单位秒) |
| total_value | int | Y | 总停车费用(单位分), 为用户从入场到现在获取订单时的总费用 |
| free_value | int | Y | 已优惠金额(单位分), 为停车场在当前停车费用时已经给予的优惠金额, 如果包涵优惠时间则该值为则free_value填写该时间等价的优惠金额+其他有效优惠金额 |
| paid_value | int | Y | 已支付金额(单位分), 为当次停车用户已经支付的金额, 比如当用户先支付了一笔后, 超时未出场重新查询订单时须返回以支付金额 |
| pay_value | int | Y | 应支付金额(单位分), 这里停车场系统需处理如果结果为负数的情况直接返回无需支付 |
| enter_free_time | int | N | 入场免费时间(单位秒) |
| buffer_time | int | N | 当前系统预留出场时间(单位秒) |
| parking_number | string | N | 车辆所在位置信息, 例如: B660 |
| pay_type | int | N | 付费类型: 6 付费入场, 其他无效 |
| car_type | int | N | 车型: 1.临停车辆, 2.非临时车 |
| car_desc | string | N | 车型类型说明 |
| recharge_expire_days | int | N | 允许过期续费天数: `<0` 不支持月卡续费, `=0` 支持月卡续费不支持过期续费, `>0` 表示允许过期x天续费, `=7` 表示允许过期7天内续费 |
| plate_type | int | N | 车牌类型: 0-中国大陆车牌, 1-境外车牌(含港澳), 2-两轮电动车车牌 |
| plate_color | int | N | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 |
| next_time | string | N | 下次跳费时间, 格式 `yyyyMMddHHmmss` |
| next_value | string | N | 下次跳费金额(总停车费), 格式 `yyyyMMddHHmmss` |

### 应答示例

```json
{
    "buffer_time": "1320",
    "charset": "UTF-8",
    "enter_free_time": "1860",
    "enter_time": "20181130100904",
    "free_value": "0",
    "paid_value": "0",
    "parking_order": "2018113010535820181130100904034792608",
    "parking_serial": "20181130100904034792608",
    "parking_time": "2694",
    "pay_value": "500",
    "plate": "粤B660PP",
    "result_code": "1001",
    "service": "service.parking.payment.billing",
    "sign": "73DF19DA361CB673DD3FEFC7A6135BE6",
    "total_value": "500",
    "version": "1.0"
}
```

---

## 2.2 同步临停缴费通知

### 接口说明

当用户完成支付后, P云将主动发起支付结果通知, 通知客户端订单支付结果。

**服务名**: `service.parking.payment.result`
**版本号**: `1.0`

> **注**: 
> - 为保证通知正常处理, 服务端可能发起多次支付结果通知, 客户端需做好去重逻辑。
> - 对于已经受到支付结果通知的订单, 应应答通知成功, 已告知服务端不必继续通知。

### 请求参数

#### 基础参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.payment.result` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

#### 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| parking_serial | string | Y | 停车流水, 原车场返回, 付费入场可能为空! |
| parking_order | string | Y | 停车支付订单号, 原车场返回 |
| gate_id | string | N | 通道编号ID, 付费时可传递触发开闸 |
| plate | string | N | 车牌号码, 当无牌车付费入场时为虚拟车牌 |
| pay_serial | string | Y | P云支付流水, 对账可用 |
| pay_time | string | Y | 支付时间, 格式: `yyyyMMddHHmmss` |
| value | int | Y | 订单应付金额(单位分), 不含优惠金额 |
| free_value | int | N | 车场优惠金额(单位分) |
| pay_origin | int | Y | 支付来源: 0=P云, 4=支付宝, 8=微信, - = 兼容其他未定义 |
| pay_origin_desc | string | Y | 支付到账说明, 例如:P云 |
| pay_source | string | N | 付款来源, 例如: 微信/支付宝/银联 |
| autopay_type | short | N | 自动扣费类型: 0非自动扣费, 1先出后扣, 2先扣后出 |
| pay_value | int | Y | 实际支付金额(单位分) |
| reduce_value | int | N | 其他(例如会员抵扣)抵扣金额(单位分) |
| revoked | short | N | 订单撤销标识: 1-已撤销、0-未撤销(仅在停车场开启了下发撤销订单会下发撤销的订单) |

### 请求示例

```json
{
    "charset": "UTF-8",
    "park_uuid": "3bad72c0-6204-4b65-90aa-4323ddd1fb5a",
    "parking_order": "201811301049350025",
    "parking_serial": "8c598afd-2cb8-4cec-8f73-4fd4391d2fc6",
    "pay_origin": "4",
    "pay_origin_desc": "支付宝",
    "pay_source": "支付宝",
    "pay_serial": "20181130105240075500112137",
    "pay_time": "20181130105250",
    "service": "service.parking.payment.result",
    "sign": "E7D7371549C2B4E3AE38C3A028973020",
    "value": "500",
    "version": "1.0"
}
```

### 应答参数

#### 基础参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.payment.result` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述, 如:返回错误信息 |

#### result_code 状态码说明

| 值 | 含义 |
|----|------|
| 1001 | 接口处理成功 |
| 1401 | 签名错误, 请检查配置 |
| 1403 | 订单已撤销。1.车辆出场 2.参考7章节 |
| 1500 | 接口内部处理失败 |

#### 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| parking_serial | string | N | 付费入场返回实际停车流水! |

### 应答示例

```json
{
    "charset": "UTF-8",
    "message": "订单支付成功",
    "result_code": "1001",
    "service": "service.parking.payment.result",
    "sign": "F97B3D16E739C82C1DED0450775905CF",
    "version": "1.0"
}
```

---

## 2.3 被扫/无感/ETC扣款请求

### 接口说明

用于停车场系统发起被扫支付、无感支付或ETC扣款请求。

**请求地址**: `POST https://api.4pyun.com/gate/1.0/parking/internal/prepay`

### 特殊说明

- `auth_code` 传了走被扫/ETC流程，如果不传该值会走无感流程。特别注意无感扣款和被扫是同一个接口。如果提示付款码无效，请重新扫码等提示多半是走了传了`auth_code`的被扫流程;
- `pay_partner` 本地扣费订单号, 同一个停车场唯一，即使第一次失败了第二次也不能相同;
- 无感返回结果: 1001=已扣款成功, 1000=代表收款请求已受理。1000的情况一般是无感平台不支持同步返回扣款结果;
- `https://api.4pyun.com/gate/1.0/parking/internal/payment` 查询扣费订单，该接口是特殊场景发起支付之后如果想主动获取支付结果，不管这个接口返回状态是成功还是失败，最终还是要受理支付异步请求回调;
- 如果支付成功 `pay_serial` 一定会有，本地可以不处理这些值，如果想直接通过1001处理支付结果放行不等异步通知结果可以直接存储 `pay_serial`, `pay_origin`, `pay_origin_desc`。还是建议等异步通知逻辑简单;
- `app_id` 和 `api_type` 一般用不上，停车场对接方式忽视掉，如果明确线下沟通了需要用到和研发同事沟通提供;
- `pay_partner` 本地扣费订单号和异步通知 `parking_order` 是同一个值，意义等价;
- `free_value`, `total_value`, `pay_value` 这些值传正确，传对了平台订单才能体现当前订单有优惠，`total_value` = `free_value` + `pay_value`;
- 必须先推送入场才能发起无感扣款，否则一定扣款失败，配合无感状态下发接口使用，不要每个车到出口都发起扣款;
- ETC扣款场景需要将车牌直接传入 `auth_code` 即可。

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID, 在停车场内部使用接口时可不传递 | string | N | op1234567723122 |
| api_type | 接口类型配合app_id使用 | string | N | api-debug |
| plate | 车牌号码 | string | Y | 京A00001 |
| plate_color | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 | string | Y | 1 |
| enter_time | 入场时间, 单位ms | long | Y | 1625154147313 |
| total_value | 总停车总费用, 单位分 | int | Y | 1000 |
| pay_value | 扣费金额, 单位分 | int | Y | 1000 |
| free_value | 优惠金额，没有优惠传0 | int | Y | 0 |
| park_uuid | 停车场UUID | string | Y | 49f0cc52-e8c7-41e3-b54d-af666b8cc11a |
| parking_serial | 停车场端的停车流水, 一般为入场记录ID | string | Y | 111112 |
| pay_partner | 本地扣费订单号, 同一个停车场唯一 | string | Y | 111112 |
| auth_code | 扣款授权码, 微信/支付宝付款码/ETC车牌 | string | N | 111112 |
| parking_time | 总停车时长, 单位秒 | int | Y | 2000 |
| gate_id | 出口扣款通道ID | string | N | 1 |
| gate_name | 出口扣款通道名称 | string | N | 通道1号 |
| sign | 请求数据签名 | string | Y | 37693CFC748049E45D87B8C7D8B9AACD |

### 请求示例

```java
public void testPrepay(){
    TreeMap<String, String> map = new TreeMap<>();
    // 车牌号码
    map.put("plate", "京A00001");
    // 扫码得到的值
    // map.put("auth_code", "135790356217418970");
    // 入场时间, 单位ms
    map.put("enter_time", (System.currentTimeMillis() - 1000 * 60 * 20) + "");
    // 优惠金额，没有优惠传0
    map.put("free_value", "0");
    // 停车场UUID
    map.put("park_uuid", "49f0cc52-e8c7-41e3-b54d-af666b8cc11a");
    // 停车场端的停车流水, 一般为入场记录ID
    map.put("parking_serial", "111112");
    // 总停车时长, 单位秒
    map.put("parking_time", "2000");
    // 本地扣费订单号, 同一个停车场唯一
    map.put("pay_partner", "11111114");
    // 扣费金额, 单位分
    map.put("pay_value", "10");
    // 总停车总费用, 单位分
    map.put("total_value", "10");
    // 通道
    map.put("gate_id", "通道ID");
    // 通道名称
    map.put("gate_name", "此路不通请绕行");
    StringBuilder builder = new StringBuilder();
    for (String key : map.keySet()) {
        builder.append(key + "=" + map.get(key) + "&");
    }
    String appSecret = "123";
    String encriptStr = builder.toString() + "app_secret=" + appSecret;
    System.out.println(encriptStr);
    String sign = MD5.encryptHEX(encriptStr);
    String keyStr = builder.toString() + "sign=" + sign;
    System.out.println(keyStr);
    try {
        Form form = Form.form();
        for (String key : map.keySet()) {
            form.add(key, map.get(key));
        }
        form.add("sign", sign);
        Response response = Request.Post("https://api.4pyun.com/gate/1.0/parking/internal/prepay")
                .bodyForm(form.build(), Charset.forName("utf-8"))
                .execute();
        HttpResponse response1 = response.returnResponse();
        System.out.println(response1.getStatusLine());
        String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
        System.out.println(text);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

### 请求返回结果参数说明

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 请求状态码 | string | Y | 1000:收款请求已受理, 1001:已扣款成功, 400:参数错误, 500:处理失败, 503:服务暂不可用 |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到，查问题尽量提供这个值 |
| pay_id | 支付结果ID | string | N | 123456789 |
| pay_serial | 平台支付流水 | string | N | 123456789 |
| pay_origin | 到账方式 | string | N | 1 |
| pay_origin_desc | 到账方式描述 | string | N | P云 |

### 请求返回结果示例

**正常返回:**
```json
{
    "code": "1001",
    "seqno": "4b1889bde31f0561",
    "data_node": "CN-South/HS3-3",
    "time_cost": 8
}
```

**参数错误（参数少传了）:**
```json
{
    "code": "1000",
    "message": "受理成功",
    "seqno": "4b1889bde31f0561",
    "data_node": "DEV/DEV-1",
    "path": "POST /gate/1.0/parking/internal/prepay"
}
```

```json
{
    "code": "400",
    "message": "请求参数错误",
    "hint": "检查请求参数`enter_time`!",
    "seqno": "aef8bf7fe6664254",
    "data_node": "DEV/DEV-1",
    "path": "POST /gate/1.0/parking/internal/prepay"
}
```

**服务器内部错误:**
```json
{
    "code": "500",
    "message": "未匹配到停车记录",
    "seqno": "1229ecc07700c7c1",
    "data_node": "CN-South/HS3-1",
    "path": "POST /gate/1.0/parking/internal/prepay"
}
```

**服务暂不可用:**
```json
{
    "code": "503",
    "message": "服务暂不可用",
    "hint": "NO HEALTH SERVICE(ParkingSyncer)!",
    "seqno": "7fb563f1fc2ca1f",
    "data_node": "DEV/DEV-1",
    "path": "POST /gate/1.0/parking/internal/prepay"
}
```

---

## 2.4 停车订单查询

### 接口说明

用于查询停车订单的支付状态。

**请求地址**: `GET https://api.4pyun.com/gate/1.0/parking/internal/payment`

### 特殊说明

1. `pay_partner` 本地扣费订单号, 同一个停车场唯一，即使第一次失败了第二次也不能相同。

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID, 在停车场内部使用接口时可不传递 | string | N | op1234567723122 |
| api_type | 接口类型配合app_id使用 | string | N | api-debug |
| park_uuid | 停车场UUID | string | Y | 49f0cc52-e8c7-41e3-b54d-af666b8cc11a |
| pay_partner | 本地扣费订单号, 同一个停车场唯一 | string | Y | 111112 |
| sign | 请求数据签名 | string | Y | 37693CFC748049E45D87B8C7D8B9AACD |

### 请求返回结果参数说明

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 请求状态码 | string | Y | 1001:查询成功(不表示扣款结果), 400:参数错误, 500:处理失败, 503:服务暂不可用 |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到，查问题尽量提供这个值 |
| status | 订单状态 | string | Y | 1=支付成功, -1=支付失败, 0=支付中 |
| pay_serial | 平台支付流水 | string | N | 123456789 |
| pay_origin | 到账方式 | string | N | 1 |
| pay_origin_desc | 到账方式描述 | string | N | P云 |

### 请求返回结果示例

**正常返回:**
```json
{
    "code": "1001",
    "seqno": "4b1889bde31f0561",
    "data_node": "CN-South/HS3-3",
    "time_cost": 8
}
```

**参数错误:**
```json
{
    "code": "400",
    "message": "请求参数错误",
    "hint": "检查请求参数`enter_time`!",
    "seqno": "aef8bf7fe6664254",
    "data_node": "DEV/DEV-1",
    "path": "GET /gate/1.0/parking/internal/payment"
}
```

**服务器内部错误:**
```json
{
    "code": "500",
    "message": "服务器内部错误",
    "seqno": "48bf04ea4caa479c",
    "data_node": "CN-South/HS3-2",
    "path": "GET /gate/1.0/parking/internal/payment"
}
```

**服务暂不可用:**
```json
{
    "code": "503",
    "message": "服务暂不可用",
    "hint": "NO HEALTH SERVICE(ParkingSyncer)!",
    "seqno": "7fb563f1fc2ca1f",
    "data_node": "DEV/DEV-1",
    "path": "GET /gate/1.0/parking/internal/payment"
}
```

---

## 2.5 停车费用计算

### 接口说明

适用于外部会员对接兼容积分抵扣时长或会员时长优惠券估算折算停车费。

**服务名**: `service.parking.payment.estimate`
**版本号**: `1.0`

> **注意**: 该接口的 `free_time` 和 `free_value` 只做费用计算，并非实际减免！！

### 请求参数

#### 基础参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.payment.estimate` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

#### 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | Y | 车牌号码 |
| parking_serial | string | Y | 停车流水 |
| free_time | int | Y | 减免时间(不含已下发优惠), 单位: 分钟 |
| free_value | int | Y | 减免金额(不含已下发优惠), 单位: 分 |

### 应答参数

#### 基础参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.payment.estimate` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述, 如:返回错误信息 |

#### result_code 状态码说明

| 值 | 含义 |
|----|------|
| 1001 | 接口处理成功 |
| 1401 | 签名错误, 请检查配置 |
| 1403 | 订单已撤销。1.车辆出场 2.参考7章节 |
| 1500 | 接口内部处理失败 |

#### 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| total_value | int | Y | 总停车费, 单位: 分 |
| free_value | int | N | 本次新增减免时间+金额对应总优惠金额, 单位: 分 |
| total_free_value | int | N | 本地停车总优惠金额(含本身优惠+预估新增部分), 单位: 分 |


---

# 3. 固定车管理接口

## 3.1 车辆信息查询

**协议说明**: P云发起向停车场查询指定车辆固定车信息，停车场返回对应车牌/客户信息。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.query |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | Y | 绑定车牌号码 |

### 应答参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.query |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1002: 没有查到相关固定车记录
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

### 应答参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| vips | array | N | 固定车集合 |

### 固定车信息

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| realname | string | Y | 客户姓名 |
| plate | string | Y | 绑定车牌号码 |
| card_no | string | N | 停车卡编号（若存在） |
| card_id | string | N | 停车卡ID（若存在） |
| charge_type | string | N | 收费标准类型标识，例如: MON_A、12 |
| charge_desc | string | N | 当前固定车收费标准描述，例如: 月卡B |
| charge_price | int | N | 收费单价，单位分 |
| mobile | string | Y | 绑定的手机号 |
| id_card | string | Y | 绑定的身份证号 |
| create_time | string | Y | 办理时间，格式: yyyyMMdd |
| expire_time | string | Y | 过期时间，格式: yyyyMMdd |
| type | int | Y | 固定车类型: 1月卡 2储值 3储次 4储天 5年卡 6季度卡 7半年卡 0免费停车 |
| balance | int | Y | 当前余额。储值: 为余额(单位分)；储次: 剩余次数；储天: 剩余天数；月卡/年卡/半年卡/季度卡: 剩余天数 |
| address | string | N | 车主单位/住宅 |
| park_space_no | string | N | 车位号 |
| renewal_limit_time | string | N | 限制时间类固定车续费不可超过的最大续费日期，格式: yyyyMMdd |

> **注**: 若月卡当前到期则 balance=0，若昨天过期 balance=-1，按照此规则计算。

### 应答示例

```json
{
  "result_code": "1001",
  "service": "service.parking.vip.query",
  "vips": "[{"balance":0,"card_id":"10760","card_no":"100766","charge_desc":"包月车_小型车","charge_price":20000,"charge_type":"22","create_time":"20210401090814","expire_time":"20210401235959","id_card":"511622199108272837","mobile":"13632693491","plate":"粤BN1B64","realname":"唐泽轩","type":1}]",
  "charset": "UTF-8",
  "message": "O.K.",
  "version": "1.0"
}
```

---

## 3.2 车辆续费结果通知

**协议说明**: P云发起请求，对固定车进行充值续费。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.renewal |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | N | 绑定车牌号码 |
| card_no | string | N | 停车卡编号（若存在） |
| card_id | string | N | 停车卡ID（若存在） |
| pay_time | string | Y | 续费时间，格式: yyyyMMddHHmmss |
| pay_serial | string | Y | 支付流水，用于对账 |
| pay_value | int | Y | 客户支付金额，单位分 |
| type | int | Y | 固定车类型: 1月卡 2储值 3储次 4储天 5年卡 6季度卡 7半年卡 0免费停车 |
| value | int | Y | 续费金额: 储值-续费金额(单位分); 储次-续费次数; 储天-续费天数; 月卡/年卡/半年卡/季度卡-续费天数 |
| quantity | int | Y | 续费数量: 储值-续费金额(单位分); 储次-续费次数; 储天-续费天数; 月卡-续费月数; 年卡-续费年数; 半年卡-续费半年数; 季度卡-续费季度数 |
| pay_origin | int | Y | 支付来源: 0 P云, 4 支付宝, 8 微信 |
| pay_origin_desc | string | Y | 支付来源说明，例如: P云 |
| pay_source | string | N | 付款来源，例如: 微信/支付宝/银联 |
| renewal_start_time | string | Y | 时间类续费开始时间，格式: yyyyMMddHHmmss |
| renewal_end_time | string | Y | 时间类续费结束时间，格式: yyyyMMddHHmmss |
| operator | string | N | 操作员姓名，例如: 小李 |

### 说明

实现该接口如果是时间类固定车，建议直接按照 renewal_start_time ~ renewal_end_time 插入新的有效时间，可实现过期续费。

**示例**: 月卡A当前有效期为 2019-01-01 ~ 2019-10-31:
- 若在 2019-10-31 之前续费一个月，则接口下发的时间范围为: 2019-11-01 ~ 2019-11-30
- 若在 2019-11-05 续费（月卡已过期），若停车场允许过期7天内续费，则续费后有两种时间计算方式:
  - 从过期时间开始: 接口下发时间范围为 2019-11-01 ~ 2019-11-30
  - 从续费时间开始计算: 接口下发时间范围为 2019-11-05 ~ 2019-12-05

车场本地其他固定车计算延期方式不是固定的，可根据下发的参数自己选择适合自己的计算方式。

### 应答参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.renewal |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1002: 没有查到相关固定车记录
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

---

## 3.3 获取固定车类型

**协议说明**: P云发起请求，查询车场固定车类型。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.charge_type.list |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |

### 说明
返回车场所有有效状态的收费标准。

### 应答参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.charge_type.list |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

### 应答参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| row | array | N | 收费类型集合 |

### 收费类型信息

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| id | string | Y | 车场收费规则ID，对应3.1和3.6接口里的charge_type值 |
| name | string | Y | 收费规则名称 |
| vip_type | int | Y | 适用固定车类型: 1月卡 2储值 3储次 4储天 5年卡 6季度卡 7半年卡 0免费停车 |
| desc | string | Y | 固定车收费描述 |
| price | int | Y | 固定车收费单价，单位分 |
| extra_days | int | N | 额外赠送时长，用于非时间类固定车续费自动延长有效期 |
| create_time | string | Y | 创建时间，格式: yyyyMMddHHmmss |
| update_time | string | Y | 更新时间，格式: yyyyMMddHHmmss |

### 应答示例

```json
{
  "service": "service.parking.charge_type.list",
  "row": "[{"id":"21","name":"测试月租A","vip_type":1,"desc":"测试月租A","price":2,"extra_days":0,"create_time":"20210329151159","update_time":"20210329151159"}]",
  "result_code": "1001",
  "charset": "UTF-8",
  "message": "操作成功",
  "version": "1.0"
}
```

---

## 3.4 新增黑名单车辆

**协议说明**: P云发起请求，将指定车辆设置为车场黑名单。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.blacklist.create |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | Y | 车牌号码 |
| realname | string | N | 车主姓名 |
| reason | string | N | 原因 |
| operator | string | N | 操作员姓名 |
| start_time | string | Y | 开始时间，格式: yyyyMMddHHmmss |
| end_time | string | Y | 结束时间，格式: yyyyMMddHHmmss |

### 说明
若存在记录直接更新。

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.blacklist.create |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

---

## 3.5 删除黑名单车辆

**协议说明**: P云发起请求，将指定车辆从车场黑名单移除。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.blacklist.delete |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | Y | 车牌号码 |
| reason | string | N | 原因 |
| operator | string | N | 操作员姓名 |

### 说明
若不存在记录直接返回操作成功！

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.blacklist.delete |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

---

## 3.6 新增固定车辆

**协议说明**: P云发起请求，将指定车辆设置为车场固定车。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.create |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | Y | 车牌号码 |
| realname | string | N | 车主姓名 |
| mobile | string | N | 联系电话 |
| id_card | string | N | 车主身份证号码 |
| card_id | string | N | 固定车卡ID |
| start_time | string | Y | 开始时间，格式: yyyyMMddHHmmss |
| end_time | string | Y | 结束时间，格式: yyyyMMddHHmmss |
| charge_type | string | Y | 收费类型，对应3.3接口返回的id，也对应3.1接口的charge_type |
| value | int | Y | 续费金额: 储值-续费金额(单位分); 储次-续费次数; 储天-续费天数; 月卡/年卡/半年卡/季度卡-续费天数 |
| quantity | int | Y | 续费数量 |
| origin_value | int | Y | 原始价格，单位分 |
| pay_value | int | Y | 用户实际付款金额，单位分 |
| pay_origin | int | Y | 支付来源: 0 P云, 4 支付宝, 8 微信 |
| pay_origin_desc | string | Y | 支付到账说明，例如: P云 |
| pay_source | string | N | 付款来源，例如: 微信/支付宝/银联 |
| address | string | N | 车主单位/住宅 |
| park_space_no | string | N | 车位号 |
| operator | string | N | 操作员姓名 |

### 说明
若存在记录则返回 1403 拒绝创建！

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.create |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1403: 固定车已存在拒绝创建
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

---

## 3.7 删除固定车辆

**协议说明**: P云发起请求，将指定车辆从车场固定车移除。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.delete |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | Y | 车牌号码 |
| card_id | string | N | 固定车卡ID |
| reason | string | N | 原因 |
| operator | string | N | 操作员姓名 |

### 说明
若不存在记录直接返回操作成功！

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.delete |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

---

## 3.8 更新固定车辆

**协议说明**: P云发起请求，强制更新存在车辆信息。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.update |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| card_id | string | Y | 固定车卡ID |
| realname | string | N | 车主姓名 |
| mobile | string | N | 联系电话 |
| id_card | string | N | 车主身份证号码 |
| address | string | N | 车主单位/住宅 |
| park_space_no | string | N | 车位号 |
| operator | string | N | 操作员姓名 |
| start_time | string | Y | 开始时间，格式: yyyyMMddHHmmss |
| end_time | string | Y | 结束时间，格式: yyyyMMddHHmmss |
| vehicle | VEHICLE | Y | 车辆集合（一位多车场景） |

### VEHICLE对象

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| plate | string | Y | 车牌号码 |
| plate_color | int | Y | 车牌颜色 |
| plate_type | int | Y | 车牌类型 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.update |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

---

## 3.9 固定车被动同步

**协议说明**: 平台调用车场查询获取当前车场所有有效的固定车集合。

### 请求参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.sync |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| sign | string | Y | 签名 |

### 请求参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |

### 应答参数 - 公共参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: service.parking.vip.sync |
| version | string | Y | 版本号: 1.0 |
| charset | string | Y | 字符集: UTF-8 |
| result_code | string | Y | 状态返回码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态码处理描述 |

**状态码说明**:
- 1001: 接口处理成功，业务参数将返回
- 1002: 没有查到相关固定车记录
- 1401: 签名错误，请检查配置
- 1500: 接口处理异常

### 应答参数 - 业务参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| row | array | N | 固定车集合 |

### 固定车信息

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| realname | string | Y | 客户姓名 |
| plate | string | Y | 绑定车牌号码 |
| card_no | string | N | 停车卡编号（若存在） |
| card_id | string | N | 停车卡ID（若存在） |
| charge_type | string | N | 收费标准类型标识 |
| charge_desc | string | N | 当前固定车收费标准描述 |
| charge_price | int | N | 收费单价，单位分 |
| mobile | string | Y | 绑定的手机号 |
| id_card | string | Y | 绑定的身份证号 |
| create_time | string | Y | 办理时间，格式: yyyyMMdd |
| expire_time | string | Y | 过期时间，格式: yyyyMMdd |
| type | int | Y | 固定车类型: 1月卡 2储值 3储次 4储天 5年卡 6季度卡 7半年卡 0免费停车 |
| balance | int | Y | 当前余额 |
| address | string | N | 车主单位/住宅 |
| park_space_no | string | N | 车位号 |
| renewal_limit_time | string | N | 限制续费最大日期，格式: yyyyMMdd |

> **注**: 若月卡当前到期则 balance=0，若昨天过期 balance=-1，按照此规则计算。

### SDK接入应答示例

```json
{
  "result_code": "1001",
  "service": "service.parking.vip.sync",
  "row": "[{"balance":0,"card_id":"10760","card_no":"100766","charge_desc":"包月车_小型车","charge_price":20000,"charge_type":"22","create_time":"20210401090814","expire_time":"20210401235959","id_card":"511622199108272837","mobile":"13632693491","plate":"粤BN1B64","realname":"唐泽轩","type":1}]",
  "charset": "UTF-8",
  "message": "O.K.",
  "version": "1.0"
}
```

### HTTP接入应答示例

```json
{
  "row": [
    {
      "charge_price": 20000,
      "card_no": "100766",
      "mobile": "13632693491",
      "id_card": "511622199108272837",
      "card_id": "10760",
      "balance": 0,
      "charge_desc": "包月车_小型车",
      "charge_type": "22",
      "plate": "粤BN1B64",
      "realname": "唐泽轩",
      "type": 1,
      "create_time": "20210401090814",
      "expire_time": "20210401235959"
    }
  ],
  "message": "O.K.",
  "result_code": "1001",
  "version": "1.0",
  "service": "service.parking.vip.sync",
  "charset": "UTF-8"
}
```

---

## 3.10 固定车主动同步

**请求地址**: `https://api.4pyun.com/gate/1.0/parking/internal/vip`

**调用方式**: HTTP POST JSON

### 特殊说明

1. 供车场批量同步本地固定车信息到平台，第一次本地需将本地存量的记录分批次同步发送，存量数据同步完成后后续的增量数据实时同步即可。仅支持POST JSON。
2. 请求签名得到的值放header请求头里面的 Authorization 字段对应的值，详细参考下面测试用例写法。

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| park_uuid | 平台分配的停车场UUID | string | Y | PARKUUID-XXXX-XXX-XXX |
| complete | 全量数据标记: 1-删除平台已有全部数据, 0-不删除历史数据正常新增数据 | int | N | 0 |
| row | 同步记录合集（ParkingVIP数组） | string | Y | [{},{},{}] |

### 请求参数 row 具体项 ParkingVIP

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| plate | 车牌号码 | string | Y | - |
| plate_color | 车辆颜色: 1蓝色 2黄色 3白色 4黑色 5绿色 -1未知 | int | Y | - |
| card_no | 逻辑卡号 | string | N | - |
| card_id | 物理卡号 | string | N | - |
| charge_type | 固定车收费类型（收费规则ID） | string | Y | MON_A |
| charge_desc | 当前固定车收费标准描述 | string | Y | - |
| status | 固定车状态: 1正常 0停用 -1删除 | int | Y | - |
| create_time | 发卡时间，时间戳单位ms | string | Y | - |
| expire_time | 过期时间，时间戳单位ms | string | Y | - |
| type | 固定车类型: 1月卡 2储值 3储次 4储天 5年卡 6季度卡 7半年卡 0免费停车(白名单) | int | Y | - |
| balance | 当前余额（储值-余额单位分; 储次-剩余次数; 储天-剩余天数; 月卡/年卡等-剩余天数; 时间卡当天过期则=0） | int | Y | - |
| realname | 车主姓名 | string | Y | - |
| mobile | 车主联系电话 | string | N | - |
| id_card | 车主身份证号码 | string | N | - |
| address | 车主单位/住宅 | string | N | - |
| park_space_no | 车位号 | string | N | - |
| operator | 操作员 | string | N | - |

### 请求返回结果参数

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 请求状态码 | string | Y | 1001:查询成功，其它状态:查看message内容 |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到，查问题尽量提供这个值 |

### 示例代码

```java
@Test
public void testVipUpload(){
    TreeMap<String, Object> map = new TreeMap<>();
    // 停车场ID
    map.put("park_uuid", "xxxxxx");
    // 全量数据标记: 1是, 0否(默认)
    map.put("complete", "0");

    Map<String, String> map1 = new HashMap<>();
    map1.put("plate", "粤X33333");
    map1.put("plate_color", "1");
    map1.put("card_no", "ID000001");
    map1.put("card_id", "ID000001");
    map1.put("charge_type", "101");
    map1.put("charge_desc", "月卡A");
    map1.put("status", "1");
    map1.put("create_time", DateUtils.parse("2021-05-30 23:59:59", "yyyy-MM-dd HH:mm:ss").getTime() + "");
    map1.put("expire_time", DateUtils.parse("2022-05-30 23:59:59", "yyyy-MM-dd HH:mm:ss").getTime() + "");
    map1.put("type", "1");
    map1.put("balance", "365");
    map1.put("realname", "张三");
    map1.put("mobile", "88888888");
    map1.put("id_card", "4310281999999999999");
    map1.put("address", "A栋1001号");
    map1.put("park_space_no", "001");
    map1.put("operator", "张三");

    map.put("row", Lists.newArrayList(map1));

    String appSecret = "123werer";
    String content = JSON.toJSONString(map);
    String encriptStr = content + "&app_secret=" + appSecret;
    String sign = MD5.encryptHEX(encriptStr);

    try {
        Response response = Request.Post("https://api.4pyun.com/gate/1.0/parking/internal/vip")
                .addHeader("Authorization", sign)
                .bodyString(content, ContentType.APPLICATION_JSON)
                .execute();
        HttpResponse response1 = response.returnResponse();
        String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
        System.out.println(text);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

---

## 3.11 黑名单车辆主动同步

**请求地址**: `https://api.4pyun.com/gate/1.0/parking/internal/blacklist`

**调用方式**: HTTP POST JSON

### 特殊说明

供车场批量同步本地黑名单信息到平台，第一次本地需将本地存量的记录分批次同步发送，存量数据同步完成后后续的增量数据实时同步即可。仅支持POST JSON。

请求签名得到的值放header请求头里面的 Authorization 字段对应的值，详情参考固定车新增测试用例写法。

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| park_uuid | 平台分配的停车场UUID | string | Y | PARKUUID-XXXX-XXX-XXX |
| complete | 全量数据标记: 1-删除平台已有全部数据, 0-不删除历史数据 | int | N | 0 |
| row | 同步记录合集（ParkingBlacklist数组） | string | Y | [{},{},{}] |

### 请求参数 row 具体项 ParkingBlacklist

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| plate | 车牌号码 | string | Y | - |
| plate_color | 车辆颜色: 1蓝色 2黄色 3白色 4黑色 5绿色 -1未知 | int | Y | - |
| card_id | 物理卡号 | string | N | - |
| realname | 车主姓名 | string | Y | - |
| reason | 原因 | string | N | - |
| status | 黑名单状态: 1正常 0停用 -1删除 | int | Y | - |
| create_time | 创建时间，时间戳单位ms | string | Y | - |
| expire_time | 数据更新时间，时间戳单位ms | string | Y | - |
| start_time | 拉黑开始时间，时间戳单位ms | string | Y | - |
| end_time | 拉黑结束时间，时间戳单位ms | string | Y | - |
| operator | 操作员 | string | N | - |

### 请求返回结果参数

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 请求状态码 | string | Y | 1001:查询成功，其它状态:查看message内容 |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到，查问题尽量提供这个值 |

---

## 3.12 固定车续费记录主动同步

**请求地址**: `https://api.4pyun.com/gate/1.0/parking/internal/vip/recharge`

**调用方式**: HTTP POST JSON

### 特殊说明

1. 供车场批量同步本地固定车续费记录到平台，第一次本地需将本地存量的记录分批次同步发送，存量数据同步完成后后续的增量数据实时同步即可。仅支持POST JSON。
2. 请求签名得到的值放header请求头里面的 Authorization 字段对应的值，详细参考下面测试用例写法。

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| park_uuid | 平台分配的停车场UUID | string | Y | PARKUUID-XXXX-XXX-XXX |
| complete | 全量数据标记: 1-删除平台已有全部数据, 0-不删除历史数据 | int | N | 0 |
| row | 同步记录合集（ParkingVIP数组） | string | Y | [{},{},{}] |

### 请求参数 row 具体项 ParkingVIP

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| plate | 车牌号码 | string | Y | - |
| plate_color | 车辆颜色: 1蓝色 2黄色 3白色 4黑色 5绿色 -1未知 | int | Y | - |
| card_no | 逻辑卡号 | string | N | - |
| card_id | 物理卡号 | string | N | - |
| charge_type | 固定车收费类型（收费规则ID） | string | Y | MON_A |
| vip_type | 固定车类型: 1月卡 2储值 3储次 4储天 5年卡 6季度卡 7半年卡 0免费停车(白名单) | string | Y | - |
| quantity | 续费单位数量，例如一个月、一季度、一年等 | int | Y | - |
| pay_partner | 车场订单号（停车场唯一） | int | Y | - |
| pay_type | 支付类型: 1现金支付; 2在线支付 | int | Y | - |
| value | 支付面额: 储值卡为金额，次卡为次数，时间卡为天数 | string | N | - |
| pay_time | 支付时间，时间戳单位ms | string | N | - |
| renewal_start_time | 续费开始时间，UTC时间 | string | N | 2022-09-30T16:00:00Z |
| renewal_end_time | 续费结束时间，UTC时间 | string | N | 2022-10-31T15:59:59Z |
| pay_value | 支付金额，单位分 | int | N | - |
| operator | 操作员 | string | N | - |

### 请求返回结果参数

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 请求状态码 | string | Y | 1001:查询成功，其它状态:查看message内容 |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到，查问题尽量提供这个值 |

### 示例代码

```java
@Test
public void testVipRechargeUpload(){
    TreeMap<String, Object> map = new TreeMap<>();
    // 停车场ID
    map.put("park_uuid", "49f0cc52-e8c7-41e3-b54d-af666b8cc11a");

    Map<String, String> map1 = new HashMap<>();
    map1.put("plate", "粤X33333");
    map1.put("plate_color", "1");
    map1.put("card_no", "ID000001");
    map1.put("card_id", "ID000001");
    map1.put("charge_type", "101");
    map1.put("vip_type", "1");
    map1.put("quantity", "1");
    map1.put("pay_partner", "pay_partner_1234567890");
    map1.put("pay_type", "1");
    map1.put("value", "30");
    map1.put("pay_time", DateUtils.parse("2021-06-30 23:59:59", "yyyy-MM-dd HH:mm:ss").getTime() + "");
    map1.put("pay_value", "10000");
    map1.put("operator", "张三");

    map.put("row", Lists.newArrayList(map1));

    String appSecret = "12323232323";
    String content = JSON.toJSONString(map);
    String encriptStr = content + "&app_secret=" + appSecret;
    String sign = MD5.encryptHEX(encriptStr);

    try {
        Response response = Request.Post("https://api.4pyun.com/gate/1.0/parking/internal/vip/recharge")
                .addHeader("Authorization", sign)
                .bodyString(content, ContentType.APPLICATION_JSON)
                .execute();
        HttpResponse response1 = response.returnResponse();
        String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
        System.out.println(text);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```


---

# 4. 停车优惠接口（PP前端）

## 1. 发放停车优惠 (mcoupon-create)

**原文链接**: https://doc.4pyun.com/parking/api/mcoupon-create.html

### 协议说明

P云通过和停车场系统完成优惠对接，将停车场优惠券实现纯电子化。
无论用户移动支付或现金支付，停车场系统均可读取到本次停车关联的停车优惠并自动抵扣停车费。

### 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.discount.create` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |
| park_uuid | string | Y | 停车场编号 |
| parking_serial | string | N | 停车流水，标识具体某次停车事件，需保证该停车场下唯一 |
| plate | string | N | 忽略停车状态派发时传递 |
| grant_serial | string | Y | 优惠券派发流水 |
| type | int | Y | 优惠类型: 1金额，2时长，3全免，4不同计价券，5长期免费券，6折扣券 |
| value | int | Y | 优惠金额：<br>当 `type=1` 时单位为分；<br>当 `type=2` 时单位为分钟；<br>当 `type=3` 时暂无定义；<br>当 `type=4` 时值无效；<br>当 `type=5` 时为在派发后单位分钟后重复进出均可免费停车；<br>当 `type=6` 时取值范围1-1000，1表示0.1%，800表示80% |
| reason | string | N | 优惠给予原因，例如: 购物满300，免费停车2小时 |
| store_name | string | Y | 当前派发优惠的商家名称 |
| store_code | string | Y | 当前派发优惠的商家唯一标识 |
| coupon_name | string | Y | 优惠券名称 |
| coupon_code | string | Y | 平台优惠券ID |
| coupon_rule | string | N | 优惠券减免规则ID，用于配置减免是减免前面还是减免后面，一般一个停车场配置相同 |
| charge_rule | string | N | 不同计价券收费规则ID，用于配置改优惠券后本次停车使用不同计价规则，一般每种优惠券不同 |
| expire_time | string | N | 指定券失效时间，格式: `yyyyMMddHHmmss` |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.discount.create` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 本次请求状态返回码：<br>1001 优惠成功<br>1002 停车信息未找到<br>1403 当前车辆已享受其他优惠<br>1401 签名错误，请检查配置<br>1500 接口处理异常 |
| sign | string | Y | 当前请求应答结果，签名方法参考附录 |
| message | string | Y | 状态码处理描述，如: 返回错误信息 |

### 注意事项

1. 优惠券的真实使用核销（一般是在参与了支付或者车辆已经出场核销之后不能再被撤销，需停车场本地控制）
2. 优惠券实际优惠值在查费用接口返回（比如优惠了2小时，最终查费用的时候按实际抵扣情况返回优惠了对应金额）
3. expire_time 如果未指定，按停车场实际有效期来（优惠券有效时间停车场系统内部定义）

---

## 2. 撤销停车优惠 (mcoupon-revoke)

**原文链接**: https://doc.4pyun.com/parking/api/mcoupon-revoke.html

### 协议说明

用于在优惠发放错误时，撤回已下发的停车优惠记录。
**注意：若优惠券已核销，应该禁止优惠券撤销！**

### 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.discount.destroy` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |
| park_uuid | string | Y | 停车场编号 |
| grant_serial | string | Y | 优惠券派发流水 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.discount.destroy` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 本次请求状态返回码：<br>1001 操作成功<br>1002 停车信息未找到<br>1401 签名错误，请检查配置<br>1500 接口处理异常 |
| sign | string | Y | 当前请求应答结果，签名方法参考附录 |
| message | string | Y | 状态码处理描述，如: 返回错误信息 |

---

## 3. 同步停车优惠状态 (mcoupon-sync)

**原文链接**: https://doc.4pyun.com/parking/api/mcoupon-sync.html

### 接口定义

- **接口地址**: `https://api.4pyun.com/gate/1.0/parking/internal/mcoupon/grant/sync`
- **调用方式**: HTTP FORM 表单提交
- **使用方式**: P云实现，由需求方调用

### 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 应用ID |
| sign | string | Y | 请求数据签名 |
| park_uuid | string | Y | 停车场UUID |
| grant_serial | string | Y | 平台下发的派发流水 |
| status | int | Y | 更新派发状态，固定值-2 |
| status_desc | string | Y | 更新派发状态说明 |
| status_time | utc时间 | N | 状态变化时间，默认取当前收到请求时间 |

### 请求返回结果参数说明

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 请求状态码，1001：成功，其它失败 |
| message | string | Y | 返回描述 |
| hint | string | N | 返回错误说明 |
| seqno | string | Y | 服务器日志标识，查日志用到 |

---

## 4. 商户优惠券状态核销 (mcoupon-sync-apply)

**原文链接**: https://doc.4pyun.com/parking/api/mcoupon-sync-apply.html

### 概述

此接口用于 **车场** 核销 **已使用** 的优惠券使用。

### 接口定义

- **接口地址**: `https://api.4pyun.com/gate/1.0/parking/internal/mcoupon/grant/sync`
- **请求方式**: `HTTP FORM 表单提交`
- **使用方式**: P云实现，由需求方调用

### 请求参数

| 参数 | 类型 | 必须 | 描述 | 示例值 |
|------|------|------|------|--------|
| sign | string | Y | 请求数据签名 | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| local_sync | int | N | 是否本地派发流水（1:是 0:否） | 0 |
| park_uuid | string | Y | 停车场UUID | |
| grant_serial | string | Y | local_sync=1时：为本地派发流水；local_sync=0时：平台下发的派发流水 | |
| status | int | Y | 派发状态：固定传1 | 1 |
| status_time | utc时间 | N | 默认当前时间 | 2022-02-28T03:30:28.000Z |
| apply_status | int | Y | 核销状态：固定传1 | 1 |
| coupon_value | int | Y | 实际优惠面额（时长券和金额券单位都是分，非时间金额券的传0） | |
| apply_value | int | Y | 实际优惠金额，单位分 | 100 |
| apply_time | utc时间 | N | 核销时间，默认取当前收到请求时间 | 2022-02-28T03:30:28.000Z |

---



---

# 5. 预约停车接口（PP前端）

## 7. 预约停车申请 (reserve-apply)

**原文链接**: https://doc.4pyun.com/parking/api/reserve-apply.html

### 说明

在可预约车场提交预约停车申请，提交的预约时间范围不表示车辆实际出入场时间，由车场系统本地根据时间范围控制是否可入场。

### 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.reserve.apply` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |
| park_uuid | string | Y | 停车场编号 |
| apply_no | string | Y | 预约单号 |
| plate | string | Y | 车牌号码 |
| start_time | string | Y | 预约开始时间，格式: `yyyyMMddHHmmss` |
| end_time | string | Y | 预约结束时间，格式: `yyyyMMddHHmmss` |
| mobile | string | N | 联系电话 |
| realname | string | N | 车主姓名 |
| reason | string | N | 预约原因 |
| pay_serial | string | N | 预约支付流水 |
| pay_value | string | N | 支付金额，单位分 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.reserve.apply` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 本次请求状态返回码：<br>1001 预约成功<br>1002 无可预约车位<br>1401 签名错误，请检查配置<br>1500 接口处理异常 |
| sign | string | Y | 当前请求应答结果，签名方法参考附录 |
| message | string | Y | 状态码处理描述，如: 返回错误信息 |

---

## 8. 取消预约停车 (reserve-cancel)

**原文链接**: https://doc.4pyun.com/parking/api/reserve-cancel.html

### 说明

在可预约车场提交预约停车申请，提交的预约时间范围不表示车辆实际出入场时间，由车场系统本地根据时间范围控制是否可入场。

### 请求参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.reserve.cancel` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |
| park_uuid | string | Y | 停车场编号 |
| apply_no | string | Y | 预约单号 |
| plate | string | N | 车牌号码 |
| reason | string | N | 取消原因 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.reserve.cancel` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 本次请求状态返回码：<br>1001 操作成功<br>1401 签名错误，请检查配置<br>1500 接口处理异常 |
| sign | string | Y | 当前请求应答结果，签名方法参考附录 |
| message | string | Y | 状态码处理描述，如: 返回错误信息 |

---



---

# 6. 停车管理接口

## 1. 人工/无牌车入场

**接口URL:** `service.parking.direct.enter` (P云调用停车场系统接口)

**说明:** 为实现无人值守，针对人工/无牌车通过扫码后平台会调用该接口完成入场开闸，若短时间重复调用该接口则不重复写入入场记录。

**付费入场流程:**
1. 车辆到入口，扫码调用"service.parking.direct.enter"
2. 若需要付费入场则返回状态码1000，并返回订单信息，必须返回parking_order和pay_value
3. 若无需付费入场则按正常开闸返回1001
4. 当入场需付费平台将引导用户完成付费，付费成功后通过"service.parking.payment.result"同步缴费结果
5. 付费成功写入缴费信息
6. 完成开闸放行

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.direct.enter` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| passport | string | Y | 用户通行证ID，停车场可用作虚拟卡ID，出场将传入，例如: 无A123456 |
| gate_id | string | Y | 通道编号ID |
| plate | string | Y | 月卡虚拟车牌，和passport等价，例如: 无A123456 |
| mobile | string | N | 用户手机号 |
| enter_time | string | N | 入场时间，格式: `yyyyMMddHHmmss` |
| operator | string | N | 操作员 |
| reason | string | N | 入场说明 |

### 应答参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.direct.enter` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 本次请求状态返回码 |
| sign | string | Y | 当前请求应答结果签名 |
| message | string | Y | 状态码处理描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1000 | 需付费入场 |
| 1001 | 操作成功 |
| 1002 | 未检测到车辆 |
| 1403 | 短时间重复入场 |
| 1500 | 接口处理异常 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| plate | string | Y | 当前识别到的车牌/虚拟车牌 |
| plate_type | int | N | 车牌类型: 0-中国大陆车牌, 1-境外车牌(含港澳), 2-两轮电动车车牌 |
| plate_color | int | N | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 |
| enter_time | string | Y | 入场时间，格式: `yyyyMMddHHmmss` |
| enter_gate | string | Y | 入口名称 |
| parking_time | int | Y | 停车时长，单位s |
| parking_serial | string | Y | 停车流水 |
| car_type | int | Y | 车型: 1.临停车辆, 2.月卡车辆, 3.贵宾车辆, 4.储值车辆, 0.其他未知 |
| car_desc | string | Y | 车类说明 |
| parking_order | string | N | 需付费情况下的付费订单号，限制单个车场唯一 |
| pay_value | int | N | 应支付金额，单位:分 |

---

## 2. 推送车辆预入场

**接口URL:**
```
POST https://api.4pyun.com/gate/1.0/parking/internal/enter/before
```

**特殊说明:** 车辆到入口上报预入场，返回是否允许入场。如果允许入场可以返回现场计费规则ID，对车辆进行指定特定计费规则。

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID，车场本地发起请求可不传递 | string | N | op1234567723122 |
| api_type | 接口类型 | string | N | XXX-SERVICE |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| park_uuid | 平台分配的停车场UUID | string | Y | PARKUUID-XXXX-XXX-XXX |
| plate | 车牌号码 | string | Y | 粤B12345 |
| plate_color | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 | short | Y | 1 |
| gate_time | 通道时间，单位ms | string | Y | 1552976318722 |
| gate_id | 通道ID | string | Y | 001 |
| gate_name | 通道名称 | string | Y | 东门入口 |
| capture_image | 当前抓拍图片 | string | N | 上传当前抓拍图片 |
| vehicle_type | 车型: 1.小车, 2.大车, -1.未知 | short | N | 1 |

---

## 3. 推送车辆入场

**接口URL:**
```
POST https://api.4pyun.com/gate/1.0/parking/internal/enter
```

**特殊说明:**

**签名计算:** 如果上传文件 `enter_image_file` 时，`enter_image_file` 文件流只参与计算得出 `enter_image_hash` 的值; `enter_image_hash` 参与最终请求 `sign` 的计算，`enter_plate_image_file` 同理。

**注意事项:**
1. 同一停车场 `parking_serial` 不能重复，重复的流水将被忽略!
2. 一般情况下不传车牌图片相关字段如 `enter_plate_image`, `enter_plate_image_file`, `enter_plate_image_hash`，该字段是特殊城市平台既要车牌拍照图片，还需要上传单独的车牌识别图片
3. 传文件有两种方式：
   - 有外网可访问图片直接传 `enter_image`
   - 传文件字节流传了 `enter_image_file` 字段 `enter_image` 会被忽视
4. 通行证信息 `idcard_name`, `idcard_no`, `idcard_image` 是部分景区强制要求上传才需要传
5. 默认城市平台上传车位数据是入一辆车车位减一，出一辆车车位加一。如果中途出现断传等现象会导致下次重新上传数据不准确，针对这种情况平台建议如果本地有条件把剩余车位 `remain_parking_space` 传上来
6. 开发对接过程中签名错误等信息不会直接返回错误状态码，对接过程中需判断返回结果里面是否有hint值

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID，车场本地发起请求可不传递 | string | N | op1234567723122 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| merchant | 平台分配的停车场商户号，`merchant` 和 `park_uuid` 二选一必传 | string | Y/N | 62626601 |
| park_uuid | 平台分配的停车场UUID，`merchant` 和 `park_uuid` 二选一必传 | string | N/Y | PARKUUID-XXXX-XXX-XXX |
| api_type | 接口类型配合app_id使用，不传app_id的不传该字段 | string | Y | XXX-SERVICE |
| parking_serial | 停车场端的停车流水，一般为入场记录ID | string | Y | PARKINGSERIAL-123456789 |
| plate | 车牌号码 | string | N | 粤B12345 |
| plate_color | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 | string | Y | 1 |
| plate_type | 车牌类型: 0-中国大陆车牌, 1-境外车牌(含港澳), 2-两轮电动车车牌, 3-特种车辆 | int | N | 1 |
| card_no | 逻辑卡号 | string | N | 1000001 |
| card_id | 物理卡号 | string | N | AED123A |
| enter_time | 入场时间，单位ms | string | Y | 1552976318722 |
| enter_image | 入场图片路径，任意路径 | string | N | https://files.4pyun.com/d/123566 |
| enter_image_file | 入场图片文件 | string | N | 字节流 |
| enter_image_hash | 入场图片文件MD5哈希，传递 `enter_image_file` 时候必须传递 | string | N | XXXXXXX |
| enter_gate | 入口名称 | string | N | 东门入口 |
| enter_security | 入口保安 | string | N | 张三 |
| car_type | 车类: 1.临停车辆, 2.月卡车辆, 3.贵宾车辆(免费车), 4.储值车辆, 0.其他未知 | string | Y | 1 |
| car_desc | 车类描述 | string | Y | 临停/月卡A |
| car_color | 车辆颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, 6.银色, 7.灰色, 8.橙色, -1 未知 | string | N | 1 |
| charge_type | 车辆计费标准 | string | Y | 1 |
| vehicle_type | 车型: 1.小车, 2.大车, 3.电动自行车/摩托车, -1.未知 | string | N | 1 |
| idcard_name | 通行证件姓名 | string | N | 阿Q |
| idcard_no | 通行证件号码 | string | N | 9527123 |
| idcard_image | 通行证件图片 | string | N | https://a.b.c/d.jpg |
| enter_release_reason | 入场人工放行原因 | string | N | 访问客户 |
| telephone | 车主联系电话 | string | N | 13800138000 |
| total_parking_space | 车场总车位数 | string | N | 100 |
| remain_parking_space | 剩余车位数 | string | N | 10 |

### 响应结果

- 该接口返回code取值200、1001、1000均表示上传成功
- HTTP非200也会返回结果，故需读取BODY

---

## 4. 推送停车信息更新

**接口URL:**
```
POST https://api.4pyun.com/gate/1.0/parking/internal/update
```

**特殊说明:**
- 同一停车场 `flow_no` 不能重复，重复的流水将被忽略!

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID，车场本地发起请求可不传递 | string | N | op1234567723122 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| park_uuid | 平台分配的停车场UUID | string | Y | PARKUUID-XXXX-XXX-XXX |
| flow_no | 变更流水号 | string | Y | FLOW000001 |
| parking_serial | 停车场端的停车流水，一般为入场记录ID | string | Y | PARKINGSERIAL-123456789 |
| plate | 车牌号码 | string | Y | 粤B12345 |
| plate_color | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 | string | N | 1 |
| plate_type | 车牌类型: 0-中国大陆车牌, 1-境外车牌(含港澳), 2-两轮电动车车牌, 3-特种车辆 | int | N | 1 |
| car_type | 车类: 1.临停车辆, 2.月卡车辆, 3.贵宾车辆(免费车), 4.储值车辆, 0.其他未知 | string | N | 1 |
| car_desc | 车类描述 | string | N | 临停/月卡A |
| enter_time | 入场时间，格式: yyyy-MM-dd'T'HH:mm:ss'Z' (UTC时间) | string | N | 2021-09-02T09:36:46.020Z |
| leave_time | 离场时间，格式: yyyy-MM-dd'T'HH:mm:ss'Z' (UTC时间) | string | N | 2021-09-02T09:36:46.020Z |
| operate_time | 操作时间，格式: yyyy-MM-dd'T'HH:mm:ss'Z' (UTC时间) | string | Y | 2021-09-02T09:36:46.020Z |
| operator | 操作员 | string | Y | 张三 |
| reason | 放行/更新原因 | string | Y | 设备异常 |

### 返回结果参数

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 请求状态码 | string | Y | 1001-成功受理; 400:参数错误; 403:访问被拦截; 500:服务器内部错误; 503:服务暂不可用 |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错误描述指导 |
| seqno | 服务器日志标示 | string | Y | 查日志用到 |

---

## 5. 推送车辆离场

**接口URL:**
```
POST https://api.4pyun.com/gate/1.0/parking/internal/leave
```

**特殊说明:**
- 签名计算规则同入场接口
- 同一停车场 `parking_serial` 不能重复
- 传文件有两种方式（同入场接口）
- 开发对接过程中判断返回结果里面是否有hint值
- 该接口返回code取值200、1001、1000均表示上传成功

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID，车场本地发起请求可不传递 | string | N | op1234567723122 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| merchant | 平台分配的停车场商户号，`merchant` 和 `park_uuid` 二选一必传 | string | Y/N | 62626601 |
| park_uuid | 平台分配的停车场UUID | string | Y | PARKUUID-XXXX-XXX-XXX |
| api_type | 接口类型配合app_id使用 | string | Y | XXX-SERVICE |
| parking_serial | 停车场端的停车流水，一般为入场记录ID | string | Y | PARKINGSERIAL-123456789 |
| plate | 车牌号码 | string | N | 粤B12345 |
| plate_color | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 | string | Y | 1 |
| plate_type | 车牌类型: 0-中国大陆车牌, 1-境外车牌(含港澳), 2-两轮电动车车牌, 3-特种车辆 | int | N | 1 |
| card_no | 逻辑卡号 | string | N | 1000001 |
| card_id | 物理卡号 | string | N | AED123A |
| enter_time | 入场时间，单位ms | string | Y | 1552976318722 |
| enter_image | 入场图片路径 | string | N | https://files.4pyun.com/d/123566 |
| enter_image_file | 入场图片文件 | string | N | 字节流 |
| enter_image_hash | 入场图片文件MD5哈希 | string | N | XXXXXXX |
| enter_gate | 入口名称 | string | N | 东门入口 |
| enter_security | 入口保安 | string | N | 张三 |
| car_type | 车类: 1.临停车辆, 2.月卡车辆, 3.贵宾车辆(免费车), 4.储值车辆, 0.其他未知 | string | Y | 1 |
| car_desc | 车类描述 | string | Y | 临停/月卡A |
| car_color | 车辆颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, 6.银色, 7.灰色, 8.橙色, -1 未知 | string | N | 1 |
| vehicle_type | 车型: 1.小车, 2.大车, 3.电动自行车/摩托车, -1.未知 | string | N | 1 |
| charge_type | 车辆计费标准 | string | Y | 1 |
| leave_time | 离场时间，单位ms | string | Y | 1552976318722 |
| leave_image | 离场图片路径 | string | N | https://files.4pyun.com/d/123566 |
| leave_image_file | 离场图片文件 | string | N | 二进制文件流 |
| leave_image_hash | 离场图片文件MD5哈希 | string | N | 37693CFC748049E45D87B8C7D8B9AACD |
| leave_gate | 出口名称 | string | N | 西门出口 |
| leave_security | 出口保安 | string | N | 李四 |
| total_value | 停车总费用，单位分 | string | N | 1000 |
| free_value | 优惠金额，单位分 | string | N | 0 |
| pay_value | 实付金额，单位分 | string | N | 1000 |
| remain_parking_space | 剩余车位数 | string | N | 10 |

---

## 6. 无感停车状态同步

**接口URL:** P云主动向停车场系统发起 (P云调用停车场系统接口)

**服务名:** `service.parking.vip`

**协议说明:** 当用户满足无感支付条件时，P云主动向停车场系统发起设置车辆自动支付权限，停车场可以根据 `credits` 在车场离场时选择是否触发主动扣费实现无感停车。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.vip` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| credits | int | Y | 当前用户可用信用额度(分)，credits大于零则开启 |
| parking_serial | string | Y | 停车流水，标识具体某次停车事件 |
| plate | string | N | 车牌 |
| pay_origin | int | N | 支付来源: 0 P云, 4 支付宝, 8 微信 |
| pay_origin_desc | string | N | 支付来源说明，例如:P云 |
| pay_source | string | N | 付款来源，例如: 微信/支付宝/银联 |
| locking | int | Y | 锁定车辆，解锁后可出场: 1锁定, 0不锁 |
| deduct_mode | string | N | 扣款模式 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.vip` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 接口处理成功 |
| 1002 | 停车记录不存在 |
| 1401 | 签名错误 |
| 1403 | 车辆已出场 |
| 1500 | 接口处理异常 |

**示例:**
```
POST https://demo.4pyun.com/parking/1.0/gateway, timeout：10000ms
Request Body：
{
  "charset" : "UTF-8",
  "sign" : "5DDE645BDCD7C54D805258B4A40A13D7",
  "park_uuid" : "1239",
  "plate" : "粤A9T58H",
  "service" : "service.parking.vip.query",
  "version" : "1.0"
}
Response Body：
{
  "result_code" : "1001",
  "message" : "OK"
}
```

---

## 7. 获取停车记录详情

**服务名:** `service.parking.detail` (P云调用停车场系统接口)

**协议说明:** 该协议主要用于处理异常订单，方便P云拉取订单详情，以便对账以及异常订单处理。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.detail` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| parking_serial | string | Y | 停车流水 |

### 应答参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.detail` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 获取成功，业务参数将返回 |
| 1002 | 未查询到停车信息 |
| 1401 | 签名错误 |
| 1500 | 接口处理异常 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| plate | string | N | 车牌号码 |
| card_no | string | N | 支付停车卡数字编号 |
| card_id | string | N | 支付停车卡物理ID |
| car_type | int | N | 车型: 1.临停车辆, 2.月卡车辆, 3.贵宾车辆, 4.储值车辆, 0.其他未知 |
| car_desc | string | N | 车型类型说明 |
| vehicle_type | int | N | 车型: 1小车, 2大车, -1未知 |
| parking_serial | string | Y | 停车流水 |
| enter_time | string | Y | 入场时间，格式 `yyyyMMddHHmmss` |
| enter_image | string | Y | 车辆入场照片访问地址 |
| enter_gate | string | Y | 车辆入场闸口标识 |
| enter_security | string | Y | 入场值班人员姓名 |
| parking_time | int | Y | 停车时长(单位秒) |
| free_value | int | N | 已优惠金额(单位分) |
| leave_time | string | N | 出场时间，格式: `yyyyMMddHHmmss` |
| leave_image | string | N | 车辆出场照片访问地址 |
| leave_gate | string | N | 车辆出场闸口标识 |
| leave_security | string | N | 出场值班人员姓名 |
| total_value | int | Y | 总停车费用(应收) |
| payments | array | Y | 支付记录列表(包括现金支付和移动支付) |

**支付信息(payments数组元素):**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| parking_order | string | N | 停车支付订单号，现金支付无需返回 |
| pay_serial | string | Y | 平台支付流水，现金支付无需返回 |
| pay_type | int | Y | 支付类型: 1现金, 2场中支付, 3自动支付, 4储值卡余额支付 |
| pay_time | string | Y | 支付时间，格式: `yyyyMMddHHmmss` |
| value | int | Y | 支付金额(单位分) |

---

## 8. 更新场内车辆信息

**服务名:** `service.parking.record.update` (P云调用停车场系统接口)

**说明:** 实现该接口后可在平台直接实现对场内车辆的车牌、入场时间及标记离场等的更新操作。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.record.update` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| parking_serial | string | Y | 停车流水，标识具体某次停车事件 |
| plate | string | N | 若传递表示需要更新车牌 |
| enter_time | string | N | 若传递表示需要更新入场时间，格式: `yyyyMMddHHmmss` |
| leave_time | string | N | 若传递表示需要标记离场并更新离场时间，格式: `yyyyMMddHHmmss` |
| reason | string | Y | 操作原因 |
| operator | string | Y | 操作人员姓名 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.record.update` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 操作成功 |
| 1401 | 签名错误 |
| 1403 | 短时间重复入场 |
| 1500 | 接口处理异常 |

---

## 9. 查询车场状态

**服务名:** `service.parking.status` (P云调用停车场系统接口)

**协议说明:** P云主动查询车场状态，包括车位信息、收费标准等。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.status` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |

### 应答参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名 |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 查询成功 |
| 1401 | 签名错误 |
| 1500 | 接口处理异常 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| total_parking_space | int | Y | 车场总车位数 |
| remain_parking_space | int | Y | 剩余车位数 |
| charge_rules | array | N | 收费规则列表 |

**收费规则对象:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| rule_id | string | Y | 规则ID |
| rule_name | string | Y | 规则名称 |
| car_type | int | Y | 适用车型 |
| first_duration | int | Y | 首时段时长(分钟) |
| first_amount | int | Y | 首时段金额(分) |
| interval_duration | int | Y | 间隔时长(分钟) |
| interval_amount | int | Y | 间隔金额(分) |
| max_fee_per_day | int | Y | 单日封顶金额(分) |
| free_period | int | N | 免费时长(分钟) |

---

## 10. 推送异常放行记录

**接口URL:**
```
POST https://api.4pyun.com/gate/1.0/parking/internal/release
```

**特殊说明:**
- 同一停车场 `parking_serial` 不能重复，重复的流水将被忽略!
- 传文件有外网可访问图片直接传 `capture_image`，否则传递文件字节流
- 开发对接过程中判断返回结果里面是否有hint值
- 正式上线之后只判断code是否为200，200代表成功

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID，车场本地发起请求可不传递 | string | N | op1234567723122 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| release_type | 放行类型: 1-入场, 2-出场 | int | Y | 1 |
| park_uuid | 平台分配的停车场UUID | string | Y | PARKUUID-XXXX-XXX-XXX |
| parking_serial | 停车场端的停车流水，一般为入场记录ID | string | Y | PARKINGSERIAL-123456789 |
| plate | 车牌号码 | string | Y | 粤B12345 |
| plate_color | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 | string | N | 1 |
| release_time | 放行时间，格式: yyyy-MM-dd'T'HH:mm:ss'Z' (UTC时间) | string | N | 2021-09-02T09:36:46.020Z |
| capture_image | 抓拍图片路径 | string | N | https://files.4pyun.com/d/123566 |
| gate_name | 通道名称 | string | N | 通道A |
| gate_id | 通道编号 | string | N | 1001 |
| car_type | 车类: 1.临停车辆, 2.月卡车辆, 3.贵宾车辆(免费车), 4.储值车辆, 0.其他未知 | string | N | 1 |
| car_desc | 车类描述 | string | N | 临停/月卡A |
| operator | 操作员 | string | N | 张三 |
| reason | 放行原因 | string | N | 设备异常 |

---

## 11. 拦截车辆

**服务名:** `service.parking.locking` (P云调用停车场系统接口)

**协议说明:** 当用户满足一定场景时，P云主动向停车场系统下发拦截该车辆，车辆在出场时，如已缴清当次停车费，停车场系统可根据该状态决定是否放行。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.locking` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| plate | string | Y | 车牌 |
| status | string | Y | 1=拦截、0=解除拦截 |
| reason | string | N | 拦截原因 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.locking` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 接口处理成功 |
| 1002 | 停车记录不存在 |
| 1401 | 签名错误 |
| 1403 | 车辆已出场 |
| 1500 | 接口处理异常 |

---



---

# 7. 设备管理接口

## 12. 车场设备列表

**服务名:** `service.parking.device.list` (P云调用停车场系统接口)

**协议说明:** P云在需要停车场本地文件业务处理时通过本协议获取指定文件。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.device.list` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |

### 应答参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名 |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 接口处理成功 |
| 1401 | 签名错误 |
| 1500 | 接口处理异常 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| device_list | array | N | 设备集合 |

**设备信息对象:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| name | string | Y | 设备名称 |
| type | int | Y | 设备类型: 1相机, 2入口通道控制器, 3出口通道控制器, 4出入口通道控制器 |
| no | string | Y | 设备编号 |
| uuid | string | Y | 设备ID(若是通道为出入口通道ID) |
| vendor | string | N | 制造商，例如: 大华/海康 |
| model | string | N | 设备型号，例如: TZ-O1 |
| status | int | Y | 状态: -1已离线, 0未知, 1正常运行 |
| status_desc | string | Y | 状态描述，例如: 脱机 |
| mac_addr | string | N | 网卡MAC地址，例如: ac:de:48:a0:11:24 |
| ipv4_addr | string | N | 网卡IP地址，例如: 172.12.1.29 |
| uptime | string | N | 启动时间，格式 `yyyyMMddHHmmss` |
| timestamp | string | N | 设备时间，格式 `yyyyMMddHHmmss` |

---

## 13. 车场通道抓拍

**服务名:** `service.parking.gate.capture` (P云调用停车场系统接口)

**协议说明:** 平台在需要抓拍的时候下发抓拍指令到车场，车场抓拍并返回图像。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.gate.capture` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| gate_id | string | Y | 通道ID |

### 应答参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名 |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 接口处理成功 |
| 1401 | 签名错误 |
| 1500 | 接口处理异常 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| image_base64 | string | N | 抓拍图片JPG格式的base64编码 |
| image_url | string | N | 抓拍图片URL |
| plate | string | N | 识别到车牌，例如: 粤B11111 |
| plate_color | string | N | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 |

---

## 14. 车场通道状态查询

**服务名:** `service.parking.gate.status` (P云调用停车场系统接口)

**协议说明:** 平台通过该接口查询当前通道控制器状态。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.gate.status` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| gate_id | string | Y | 通道ID |

### 应答参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名 |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 接口处理成功 |
| 1401 | 签名错误 |
| 1500 | 接口处理异常 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| status | string | Y | 通道状态: **OPEN**-打开(已抬杆), **CLOSE**-关闭(已落杆), **KEEP_OPEN**-设置常开 |
| status_desc | string | N | 状态说明，例如: 设备控制常开 |
| capture_image_base64 | string | N | 实时抓拍图片JPG格式的base64编码 |
| capture_image_url | string | N | 实时抓拍图片URL |
| plate | string | N | 识别到车牌，例如: 粤B11111 |
| plate_color | string | N | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 |

---

## 15. 车场通道控制

**服务名:** `service.parking.gate.control` (P云调用停车场系统接口)

**协议说明:** 平台可通过调用该接口控制通道打开关闭或者设置常开等。

### 请求参数

**公共参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名: `service.parking.gate.control` |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| sign | string | Y | 签名 |

**业务参数:**

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| park_uuid | string | Y | 停车场编号 |
| gate_id | string | Y | 通道ID |
| status | string | N | 通道状态: **OPEN**-打开(已抬杆), **CLOSE**-关闭(已落杆), **KEEP_OPEN**-设置常开(保持打开,不落杆) |
| reason | string | N | 操作原因说明，例如: 设备控制常开 |
| plate | string | N | 设置当前识别车牌，例如: 粤B11111 |
| plate_color | string | N | 车牌颜色: 1.蓝色, 2.黄色, 3.白色, 4.黑色, 5.绿色, -1 未知 |

### 应答参数

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| service | string | Y | 服务名 |
| version | string | Y | 版本号: `1.0` |
| charset | string | Y | 字符集: `UTF-8` |
| result_code | string | Y | 状态码 |
| sign | string | Y | 签名 |
| message | string | Y | 状态描述 |

**result_code状态码:**

| 状态码 | 含义 |
|--------|------|
| 1001 | 接口处理成功 |
| 1401 | 签名错误 |
| 1500 | 接口处理异常 |

---



---

# 8. 车场进件接口

## 16. 提交车场进件

**接口URL:**
```
POST https://api.4pyun.com/gate/1.0/parking/park/apply
```

**Content-Type:** `application/json`

### 1.1 应用ID与密钥

AppId是所有P云接口的公共参数，每个开发者会由平台分配唯一的AppId，与AppId唯一对应的AppSecret则用于生成接口参数的签名，所有接口请求需传递AppId以及签名Sign。

### 1.2 签名规则

对于通过HTTP Body以格式为 **application/json** 传参的请求，签名规则如下：

1. 将密钥以"&"为分隔符添加到请求体中的JSON字符串后面，生成待签名字符串，如：`{"a"="string", "b"=0, "c"=1900000109}&app_secret=7d15453e97507ef794cf7b0519d`
2. 使用标准MD5算法对待签名字符串进行签名
3. 将签名值写入请求头中的"Authorization"字段

**POST application/json请求示例代码：**
```java
String appId = "op010728c14869c8bf4";
String appSecret = "79B0F3EJF83JF272D9E74FABD95EDE";

Map<String, Object> params = new HashMap<>();
params.put("app_id", appId);
params.put("park_uuid", "e24deadf-1aa0-4981-bde5-f9c474c4f5f5");
String jsonStr = JSON.toJSONString(params);

String encryptStr = jsonStr + "&app_secret=" + appSecret;
String sign = MD5.encryptHEX(encryptStr);

Response response = Request.Post("https://api.4pyun.com/gate/1.0/parking/park/apply")
        .setHeader("Authorization", sign)
        .bodyString(jsonStr, ContentType.APPLICATION_JSON)
        .execute();
```

### 2.1 停车场创建接口

**说明：** 创建停车场

**请求方法：** POST

### 请求参数

| 参数名 | 必填 | 说明 | 类型 | 示例 |
|--------|------|------|------|------|
| app_id | 是 | 平台分配应用ID | string | op72***24 |
| parent | 是 | 平台分配厂商商户号 | string | PV3**4 |
| park_name | 是 | 车场名称，在平台中要求唯一，不可重复 | string | 测试-停车场 |
| address | 是 | 详细地址 | string | 广东省深圳市深圳湾科技生态园10栋A2401 |
| coordinate | 是 | 经纬度坐标 | object | - |
| parking_space | 是 | 车位数 | int | 50 |
| manager_mobile | 是 | 密保手机号，将用于创建超管账号 | string | 13600000162 |
| service_phone | 是 | 客服电话 | string | 0755-28823700 |
| type | 是 | 车场分类 | string | OFFICE |
| software_code | 是 | 软件编码（联系技术确认） | string | POXXXXX |
| software_param | 是 | 软件参数，具体参数由软件编码决定 | map | {"app_secret": "XXXXXX"} |
| charge_image | 是 | 收费牌照片 | string | - |
| enter_gate_image | 否 | 入口图片 | string | - |
| leave_gate_image | 否 | 出口图片 | string | - |
| park_image | 否 | 停车场照片 | string | - |

**type车场分类枚举值:**

| 枚举值 | 说明 |
|--------|------|
| RESIDENCE | 住宅 |
| OFFICE | 写字楼 |
| MALL | 商业 |
| PUBLIC_PLACE | 公共 |
| OTHER | 其他 |
| TEST | 测试 |

**coordinate坐标对象:**

| 参数名 | 必填 | 说明 | 类型 | 示例 |
|--------|------|------|------|------|
| type | 是 | 坐标类型 | string | GCJ02 |
| latitude | 是 | 纬度 | double | 22.690244 |
| longitude | 是 | 经度 | double | 113.937153 |

**坐标类型枚举值:**

| 枚举值 | 说明 |
|--------|------|
| WGS84 | 地球坐标系 |
| BD09 | 百度坐标系 |
| GCJ02 | 火星坐标系 |

### 请求示例

```json
{
    "parent": "PV376764",
    "service_phone": "13800138000",
    "address": "广东省深圳市宝安区坑尾大道1号石岩公馆",
    "coordinate": {
        "latitude": 22.690244,
        "type": "GCJ02",
        "longitude": 113.937153
    },
    "software_code": "PO9314",
    "parking_space": 1,
    "manager_mobile": "159****0963",
    "type": "OTHER",
    "park_name": "测试进件230404_1",
    "app_id": "op00961963581daa7",
    "software_param": {
        "app_secret": "lKdPpkhXUfeCyPXI"
    },
    "charge_image": "https://xxx-charge_image.png",
    "enter_gate_image": "https://xxx-enter_gate_image.png",
    "leave_gate_image": "https://xxx-leave_gate_image.png",
    "park_image": "https://xxx-park_image.png"
}
```

### 返回参数

| payload参数名 | 说明 | 类型 | 示例 |
|---------------|------|------|------|
| park_code | 车场商户号 | string | 18695715 |
| park_uuid | 车场UUID | string | 6e431a23-08cc-4b36-ad03-79f6fa643717 |
| park_name | 车场名称 | string | 测试进件230404_1-停车场 |
| manager_account | 超管账号 | string | 18695715 |
| manager_mobile | 密保手机号 | string | 159****0963 |

### 3. 支付渠道申请链接

**跳转链接示例：**
```
https://mch.4pyun.com/external/payment/channel/apply?merchant=xxx&token=xxx
```

**说明：** 跳转链接前需保证车场已在平台创建(调用 2.1 停车场创建接口)

**URL:** https://mch.4pyun.com/external/payment/channel/apply

**参数：**
- merchant: P云平台车场商户号(调用 2.1 停车场创建接口 返回park_code参数)
- token: 请求令牌

### 3.2 令牌生成规则

```javascript
let content = JSON.stringify({
    iss: 'op01958c1095c8bf4', // 应用ID
    iat: (+new Date() / 1000).toFixed(0), // 令牌生成时间戳(单位秒)
    exp: ((+new Date() + 24 * 60 * 60 * 1000) / 1000).toFixed(0), // 令牌过期时间(单位秒)
})
let secret = "79B0F3E6E956A6F272D9E74FABD95EDE"; //应用ID密钥
let signature = MD5.hashBinary(content + "&key=" + secret); // 生成签名
let token = "Basic " + Base64.encode(content) + "." + signature; //生成令牌
// 注意"Basic"后有一空格
// UrlEncode
token = encodeURI(token)
```

---

# 附录

## 接口地址

- 测试环境：`https://dev-api.4pyun.com`
- 生产环境：`https://api.4pyun.com`

## 签名规则

### 计算方式

1. 先过滤掉值为空的参数
2. 将所有业务参数按照参数名进行升序排序
3. 以 HTTP URL 风格拼接成待签名的字符串，如：`a=1&b=2&c=123`
4. 将密钥以 `&app_secret=您的密钥` 形式拼接到上一步待签名的字符串后面，如：`a=1&b=2&c=123&app_secret=您的密钥`
5. 使用标准 MD5 算法对上述字符串进行签名（忽略大小写）
6. 将签名值进行 HTTP 请求即可

### 传递方式

> Content-Type=application/x-www-form-urlencoded

## 公共状态码

| 状态码 | 含义 |
|--------|------|
| 200 | HTTP请求成功 |
| 1000 | 请求已受理/需付费入场 |
| 1001 | 业务处理成功 |
| 1002 | 业务处理失败/记录不存在 |
| 1401 | 签名错误，请检查配置 |
| 1403 | 短时间重复请求/车辆已出场 |
| 1500 | 接口处理异常 |
| 400 | 参数错误 |
| 403 | 访问被拦截 |
| 500 | 服务器内部错误 |
| 503 | 服务暂不可用 |

## 车牌颜色对照表

| 值 | 颜色 |
|----|------|
| 1 | 蓝色 |
| 2 | 黄色 |
| 3 | 白色 |
| 4 | 黑色 |
| 5 | 绿色 |
| -1 | 未知 |

## 车牌类型对照表

| 值 | 类型 |
|----|------|
| 0 | 中国大陆车牌 |
| 1 | 境外车牌(含港澳) |
| 2 | 两轮电动车车牌 |
| 3 | 特种车辆(武警类型车辆等) |

## 车型对照表

| 值 | 类型 |
|----|------|
| 1 | 临停车辆 |
| 2 | 月卡车辆 |
| 3 | 贵宾车辆(免费车) |
| 4 | 储值车辆 |
| 0 | 其他未知 |

## 车辆颜色对照表

| 值 | 颜色 |
|----|------|
| 1 | 蓝色 |
| 2 | 黄色 |
| 3 | 白色 |
| 4 | 黑色 |
| 5 | 绿色 |
| 6 | 银色 |
| 7 | 灰色 |
| 8 | 橙色 |
| -1 | 未知 |

## 支付方式对照表

| 值 | 方式 |
|----|------|
| 1 | 现金 |
| 2 | 场中支付 |
| 3 | 自动支付 |
| 4 | 储值卡余额支付 |


---

# ==================== 附录 ====================

---

# 附录A：电子发票接口（开放平台）

## 14. 提交开票申请 (invoice-obtain)

**原文链接**: https://doc.4pyun.com/openapi/api/invoice-obtain.html

### 请求地址

```
https://api.4pyun.com/gate/1.0/invoice/obtain
```

### 调用方式

```
HTTP POST FORM 表单提交
```

### 特殊说明

```
1: app_id, app_secret 用户身份id和加密密钥由平台方提供，对接方需提供公司全称然后给到商务提交给研发申请
2: buyer_telephone buyer_address 要么都有值要么都没有值
3: buyer_bank_name, buyer_bank_account 要么都有值要么都没有值
4: 企业开票也就是buyer_tax_type=1 buyer_tax_no 必须传 buyer_name必须是公司的名称不然开出来的票可能报销不了
5: 企业开票也就是buyer_tax_type=2 buyer_tax_no 不传 buyer_name客户随便填不能有空格但是必须传该字段
```

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID | string | Y | op1234567723122 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| merchant | 停车场商户号 | string | Y | 6262666666 |
| obtain_order | 合作方请求开票订单号，要求同一app_id下唯一 | string | Y | obtainOrder-XXXXXXXXXXXXX |
| value | 开票金额（单位分） | string | Y | 1000 |
| buyer_tax_type | 1:企业开票 2:个人开票 | string | Y | 1 |
| buyer_tax_no | 购方(车主)公司纳税识别号 | string | N | XXXXXXXXXXXXXXX |
| buyer_name | 购方名称，如果传了购方税号一定要填购方公司名称，如果没有传购方税号可以随意传但是不能为空 | string | Y | 深圳市神州XXXXXXXXX有限公司 |
| buyer_email | 接收开票结果邮箱 | string | Y | 1916714111111@qq.com |
| buyer_mobile | 接收开票成功短信手机号，并不是所有开票商都支持发短信建议有就传 | string | N | 18075521111 |
| buyer_telephone | 购买方公司联系电话 | string | N | 075588888888 |
| buyer_address | 购买方公司联系地址 | string | N | XXXXXXX |
| buyer_bank_name | 购买方公司银行卡名称 | string | N | 银行名称 |
| buyer_bank_account | 购买方公司银行卡账号 | string | N | 银行账户 |
| notify_url | 开票成功或者失败的回调后台地址 | string | Y | https://www.hello.com |
| extra | 附加业务参数，用于生成发票备注模版 | string | N | {"memo":"发票备注"} |

### 请求示例

```java
1.1：签名前字符串
    str=app_id=op6619067c70f1234213&buyer_email=191671412@qq.com&buyer_mobile=18075521111&buyer_name=随便开票&buyer_tax_no=91440300067123423&buyer_tax_type=1&merchant=626266016&obtain_order=123456789111&value=100&app_secret=d6d3ea8f910b9a3ffa2341234
1.2：MD5(str)
    sign=B10DE5E8A1B5E8B16878AE49C917C212

   @Test
    public void testObtain() {
        TreeMap<String, String> map = new TreeMap<>();
        // 平台分配的接入应用ID
        map.put("app_id", "op6619067c70f1234213");
        // 停车场商户号
        map.put("merchant", "626266016");
        // 合作方请求开票订单号，要求同一app_id下唯一
        map.put("obtain_order", "123456789111");
        // 开票金额(单位分)
        map.put("value", "100");
        // 1:企业开票 2:个人开票
        map.put("buyer_tax_type", "1");
        // 购方(车主)公司纳税识别号
        map.put("buyer_tax_no", "91440300067123423");
        // 购方名称
        map.put("buyer_name", "随便开票");
        // 接收开票结果邮箱
        map.put("buyer_email", "191671412@qq.com");
        // 接收开票成功短信手机号
        map.put("buyer_mobile", "18075521111");

        StringBuilder builder = new StringBuilder();
        for (String key : map.keySet()) {
            builder.append(key + "=" + map.get(key) + "&");
        }
        String appSecret = "d6d3ea8f910b9a3ffa2341234";
        String encriptStr = builder.toString() + "app_secret=" + appSecret;
        System.out.println(encriptStr);
        String sign = MD5.encryptHEX(encriptStr);
        String keyStr = builder.toString() + "sign=" + sign;
        System.out.println(keyStr);
        try {
            Form form = Form.form();
            for (String key : map.keySet()) {
                form.add(key, map.get(key));
            }
            form.add("sign", sign);
            Response response = Request.Post("https://api.4pyun.com/gate/1.0/invoice/obtain")
                    .bodyForm(form.build(), Charset.forName("utf-8"))
                    .execute();
            HttpResponse response1 = response.returnResponse();
            System.out.println(response1.getStatusLine());
            String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
            System.out.println(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
```

### 请求返回结果参数说明

| 字段名称 | 字段说明 | 类型 | 必填 | 备注 |
|----------|----------|------|------|------|
| code | 请求状态码 | string | Y | 1001: 开票成功<br>1000: 开票受理成功<br>其它: 读取message信息 |
| message | 返回描述 | string | Y | 返回描述 |
| hint | 返回错误说明 | string | N | 返回具体错描述指导 |
| seqno | 服务器日志标识 | string | Y | 查日志用到，查问题尽量提供这个值 |
| obtain_serial | 平台开票编号 | string | N | XXXXXXXXX |

### 请求返回结果示例

```json
// 开票受理中
{
    "code": "1000",
    "message": "开票中",
    "seqno": "3e6af71d2a4e7eec",
    "data_node": "CN-South/HS3-3",
    "time_cost": 838,
    "payload": {
        "obtain_serial": "202107212029510755019876"
    }
}
```

```json
// 开票成功
{
    "code": "1001",
    "message": "开票成功",
    "seqno": "3e6af71d2a4e7eec",
    "data_node": "CN-South/HS3-3",
    "time_cost": 838,
    "payload": {
        "obtain_serial": "202107212029510755019876"
    }
}
```

```json
// 服务器内部错误
{
    "code": "500",
    "message": "商户号不支持电子发票",
    "seqno": "b92e71a7ee445885",
    "data_node": "CN-South/HS3-3",
    "path": "POST /gate/1.0/invoice/obtain"
}
```

---

## 15. 查询开票详情 (invoice-query)

**原文链接**: https://doc.4pyun.com/openapi/api/invoice-query.html

### 请求地址

```
https://api.4pyun.com/gate/1.0/invoice/invoice
```

### 调用方式

```
HTTP GET
```

### 特殊说明

```
1: app_id, app_secret 用户身份id和加密密钥由平台方提供，对接方需提供公司全称然后给到商务提交给研发申请
2: 状态码code 1001只代表请求结果正常不代表开票成功，开票结果认status
```

### 请求参数

| 字段名称 | 字段说明 | 类型 | 必填 | 示例 |
|----------|----------|------|------|------|
| app_id | 平台分配的接入应用ID | string | Y | op1234567723122 |
| sign | 请求数据签名 | string | Y | C65FCAC2D3FB5E2D3D4AD93DD20C8C39 |
| merchant | 停车场商户号 | string | Y | 6262666666 |
| obtain_order | 合作方请求开票订单号，要求同一app_id下唯一 | string | Y | obtainOrder-XXXXXXXXXXXXX |

### 请求示例

```java
签名前字符串
1.1：签名前字符串
    str=app_id=op6619067c70f1234213&merchant=626266016&obtain_order=123456789111&app_secret=d6d3ea8f910b9a3ff1234234
1.2：MD5(str)
    sign=CF56BA852C2F8DF9CD8D3DFB94C56DC0

     @Test
    public void testInvoiceDetail() {
        TreeMap<String, String> map = new TreeMap<>();
        // 平台分配的接入应用ID
        map.put("app_id", "op6619067c70f1234213");
        // 停车场商户号
        map.put("merchant", "626266016");
        // 合作方请求开票订单号，要求同一app_id下唯一
        map.put("obtain_order", "123456789111");
        StringBuilder builder = new StringBuilder();
        for (String key : map.keySet()) {
            builder.append(key + "=" + map.get(key) + "&");
        }
        String appSecret = "d6d3ea8f910b9a3ff1234234";
        String encriptStr = builder.toString() + "app_secret=" + appSecret;
        System.out.println(encriptStr);
        String sign = MD5.encryptHEX(encriptStr);
        String keyStr = builder.toString() + "sign=" + sign;
        String url = "https://api.4pyun.com/gate/1.0/invoice/detail?";
        System.out.println(url + keyStr);
        try {
            Response response = Request.Get(url + keyStr).execute();
            HttpResponse response1 = response.returnResponse();
            System.out.println(response1.getStatusLine());
            String text = IOUtils.toString(response1.getEntity().getContent(), "utf-8");
            System.out.println(text);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

---

## 16. 开票结果异步通知 (invoice-sync/notify)

**原文链接**: https://doc.4pyun.com/openapi/api/invoice-sync.html

### 通知地址

开票请求接口请求参数中的 notify_url

### 描述

合作方实现该接口用于接受支付结果通知，若通知未能返回 `1001` 不成功则会重复通知多次。

### 调用方式

```
HTTP POST FORM 表单提交
```

### 特殊说明

```
合作方实现该接口用于接受电子发票开票结果通知，若通知未能返回1001不成功则会重复通知多次。
```

### 请求参数（平台通知合作方）

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| app_id | string | Y | 平台分配的接入应用ID |
| sign | string | Y | 请求数据签名 |
| merchant | string | Y | 商户号 |
| obtain_order | string | Y | 合作方开票请求订单 |
| obtain_serial | string | Y | 平台方开票唯一标识 |
| status | short | Y | 开票状态: 2 开票成功、-1 开票失败 |
| status_desc | string | N | 状态说明 |
| invoice_code | string | N | 发票代码 |
| invoice_no | string | N | 发票号码 |
| verify_code | string | N | 发票校验码 |
| invoice_url | string | N | 发票链接 |
| invoice_time | long | N | 交易时间，unix时间戳，单位ms |

### 响应参数（合作方返回给平台）

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| code | string | Y | 业务处理状态码<br>1001: 通知成功<br>其它状态码: 读取message |
| message | string | N | 业务处理状态说明 |
| hint | string | N | 提示说明 |

### 请求返回结果示例

```json
// 成功返回
{
    "code": "1001",
    "message": "通知成功",
    "hint": ""
}
```

---

# 附录：停车优惠事件触发（开放平台）

## 停车优惠事件触发 (mcoupon-event)

**原文链接**: https://doc.4pyun.com/openapi/parking/mcoupon-event.html

### 事件优惠下发

一个第三方服务对应一个车场的下发优惠券类型固定，由平台配置

### 1.1) 请求地址

```
https://api.4pyun.com/gate/1.0/parking/mcoupon/event
```

### 1.2) 调用方式

```
HTTP POST FORM 表单提交
```

### 1.3) 特殊说明

```
1: app_id, app_secret 用户身份id和加密密钥由平台方提供，对接方需提供公司全称然后给到商务提交给研发申请
2: merchant, store_code分别代表停车场商户号和商家商户号，两个参数由停车场物业那边提供
3: coupon_code是具体一种优惠券的券ID，目前物业创建好优惠券充值好券之后截图然后找研发同事复制出来该值
4: 返回状态码code:1001代表派发成功，其它状态码直接读取message提示信息
5: 特别注意即使http状态非200也要把返回内容读取出来，会返回具体错误原因
```

### 1.4) 请求参数

| 字段名称 | 类型 | 必填 | 字段说明 |
|----------|------|------|----------|
| app_id | string | true | 平台分配的接入应用ID |
| api_store_code | string | true | 车场对应第三方平台ID |
| api_type | string | true | 第三方服务类型（该字段由平台分配，固定） |
| event_id | string | true | 第三方订单号，需保证在第三方平台唯一 |
| plate | string | true | 车牌号 |
| subject | string | true | 优惠事件描述 |
| metadata | map | false | 业务元数据 |
| sign | string | true | 签名 |

### EventMetadata

| Name | Type | Required | Description |
|------|------|----------|-------------|
| name | string | true | 数据名称，该参数仅用于数据值说明 |
| value | int32 | true | 数据值，调用方的业务变量 |

### 1.7) 请求返回结果示例

```json
// 正常返回
{
 "code": "1001",
 "data_node": "HS1-1",
 "seqno": "86213123",
 "payload": {
 "grant_serial": "xxxxx"
 },
 "time_cost": 100,
 "message": "操作成功"
}
```

```json
// 参数错误
{
    "code": "400",
    "message": "请求参数错误",
    "hint": "`merchant` Required!",
    "seqno": "94929a9b0874aa46",
    "data_node": "CN-South/HS3-2",
    "path": "POST /gate/1.0/parking/mcoupon/grant/create"
}
```

```json
// 服务器内部错误
{
    "code": "1500",
    "message": "没有匹配到停车记录",
    "seqno": "57804ae2e859f58a",
    "data_node": "CN-South/HS3-1",
    "time_cost": 239,
    "payload": {
        "grant_serial": ""
    }
}
```

---

*文档结束*
*© 2026 Shenzhen ChinaRoad Technology Co., Ltd. All Rights Reserved*


---

# 附录B：签名规则汇总

## B.1 URL传参/Form表单传参签名规则

1. 先将所有业务参数按照参数名（不包括 sign）进行升序排序（若有多个相同参数名则继续按照其参数值进行升序排序）;
2. 以 'http url' 参数风格拼接成待签名的字符串，如：`a=v&b=0&c=1900000109&d=102`
3. 再将密钥拼接到待签名的字符串后面，如：`a=v&b=0&c=1900000109&d=102&app_secret=XXXXX`
4. 使用标准MD5算法对待签名字符串进行签名;
5. 将签名值写入 'sign' 字段;

## B.2 application/json 传参签名规则

1. 将请求JSON报文整体作为字符串;
2. 在JSON字符串后面拼接 `&app_secret=${app_secret}`;
3. 使用标准MD5算法对整体进行签名;
4. 签名值放在请求头的 `Authorization` 字段中;

## B.3 公共状态码说明

| 状态码 | 含义 |
|--------|------|
| 1000 | 请求已受理/处理中 |
| 1001 | 操作成功 |
| 1002 | 记录不存在/未查询到信息 |
| 1003 | 余额不足/其他业务限制 |
| 1401 | 签名错误，请检查配置 |
| 1403 | 权限不足/记录已存在/拒绝操作 |
| 1405 | 操作失败 |
| 1500 | 接口处理异常/服务器内部错误 |
| 400 | 请求参数错误 |
| 403 | 访问被拦截 |
| 500 | 服务器内部错误 |
| 503 | 服务暂不可用 |

---

> **文档说明:** 本文档内容由P云开放平台官方文档抓取整理
> 
> **原始文档地址:**
> - 非PP前端: https://doc.4pyun.com/openapi/guideline/payment-policy.html
> - PP前端: https://doc.4pyun.com/parking/
> 
> **版权所有:** Shenzhen ChinaRoad Technology Co., Ltd. All Rights Reserved
