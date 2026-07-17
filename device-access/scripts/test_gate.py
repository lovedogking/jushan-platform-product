"""
臻识 C5H 道闸控制测试脚本
直接通过 MQTT 下发 gpio_out 命令，测试不同 io/value/delay 组合
"""
import paho.mqtt.client as mqtt
import json
import uuid
import time
import threading

BROKER = "192.168.20.143"
PORT = 1883
DEVICE_SN = "917e2298-8ddf3e46"
TOPIC_DOWN = f"device/{DEVICE_SN}/message/down/gpio_out"
TOPIC_UP = f"device/{DEVICE_SN}/message/up/#"

responses = []
response_event = threading.Event()

def on_connect(client, userdata, flags, rc):
    print(f"[MQTT] Connected, rc={rc}")
    client.subscribe(TOPIC_UP)

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        name = payload.get("name", "?")
        code = payload.get("code", "?")
        print(f"[RESP] topic={msg.topic}  name={name}  code={code}  payload={json.dumps(payload, ensure_ascii=False)}")
        responses.append({"topic": msg.topic, "payload": payload})
        response_event.set()
    except Exception as e:
        print(f"[RESP] raw: {msg.payload.decode()}")

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message
client.connect(BROKER, PORT, 60)
client.loop_start()
time.sleep(1)

# Ensure connected
if not client.is_connected():
    print("ERROR: MQTT connection failed!")
    exit(1)

# Test combinations
# io: which IO pin (0, 1, 2, etc.)
# value: 0=low/disconnect, 1=high/connect
# delay: pulse duration in ms (0 = latch/keep state)
test_cases = [
    # (io, value, delay, description)
    ("开闸尝试1：IO0拉高500ms脉冲", 0, 1, 500),
    ("开闸尝试2：IO1拉高500ms脉冲", 1, 1, 500),
    ("开闸尝试3：IO0拉高1000ms脉冲", 0, 1, 1000),
    ("开闸尝试4：IO0拉高持续(无delay)", 0, 1, 0),
    ("关闸尝试1：IO0拉低500ms脉冲", 0, 0, 500),
    ("关闸尝试2：IO1拉低500ms脉冲", 1, 0, 500),
    ("关闸尝试3：IO0拉低持续(无delay)", 0, 0, 0),
]

print("=" * 60)
print(f"臻识 C5H 道闸控制测试 - 设备: {DEVICE_SN}")
print(f"下发 Topic: {TOPIC_DOWN}")
print("=" * 60)

for desc, io, value, delay in test_cases:
    print(f"\n---> {desc}  (io={io}, value={value}, delay={delay})")
    response_event.clear()
    responses.clear()

    msg_id = uuid.uuid4().hex[:16]
    now = int(time.time())

    body = {"io": io, "value": value}
    if delay > 0:
        body["delay"] = delay

    payload = {
        "id": msg_id,
        "sn": DEVICE_SN,
        "name": "gpio_out",
        "version": "1.0",
        "timestamp": now,
        "payload": {
            "type": "gpio_out",
            "body": body
        }
    }

    json_str = json.dumps(payload)
    print(f"  SEND: {json_str}")
    client.publish(TOPIC_DOWN, json_str)

    # Wait for response with timeout
    if response_event.wait(timeout=5):
        print(f"  -> Got response(s)")
    else:
        print(f"  -> NO RESPONSE (timeout 5s)")

    time.sleep(2)  # Gap between tests

client.loop_stop()
client.disconnect()
print("\n" + "=" * 60)
print("测试完成")
print("=" * 60)
