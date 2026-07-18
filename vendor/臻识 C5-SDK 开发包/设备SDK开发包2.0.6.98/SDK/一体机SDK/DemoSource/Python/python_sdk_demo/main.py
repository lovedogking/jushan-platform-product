#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import time

from lib3rd.vzsdk import lpr_sdk as sdk
from lib3rd.define import *
from ctypes import *


def plate_info_callback_result(sdk_hdl, user_data, plate_result, plate_num, result_type, img_full, img_plate_clip):
    print("plate_info_callback_result", plate_result, plate_num, result_type)


def serial_recv_callback_result(sdk_hdl, recv_data, recv_size, user_data):
    print("serial_recv_callback_result", recv_data, recv_size)


def common_notify_callback_result(sdk_hdl, user_data, notify, detail):
    print("common_notify_callback_result", notify, detail)


def find_device_callback_result(dev_name, ip_addr, us_port, us_type, sl, sh, netmask, gateway, user_data):
    print("find_device_callback_result", dev_name, ip_addr, us_port, us_type, sl, sh, netmask, gateway, user_data)


def whitelist_query_callback_result(cbtype, vehicle, customer, user_data):
    print("whitelist_query_callback_result", cbtype, vehicle, customer, user_data)


if __name__ == '__main__':
    ret = sdk.setup()
    print("setup", ret == 0)

    # handle = sdk.open("192.168.13.202")
    handle = sdk.open("192.168.55.14")
    print("open device", handle != 0)

    state_connect = c_char()
    print("is_connected start", int.from_bytes(state_connect.value, byteorder="big", signed=True))
    ret = sdk.is_connected(handle, byref(state_connect))
    print("is_connected end", ret == 0, int.from_bytes(state_connect.value, byteorder="big", signed=True))

    device_ip = (c_byte * 32)()
    ret = sdk.get_device_ip(handle, device_ip, 32)
    print("get_device_ip", ret)
    print(''.join([chr(i) for i in device_ip]).strip('\x00'))

    # img_size = 1280*720
    # img_buffer = (c_byte * img_size)()
    # ret = sdk.get_snap_image(device, img_buffer, img_size)
    # print("get_snap_image", ret, type(img_buffer))
    # with open("snap_image.jpg", "wb") as f:
    #     for data in img_buffer[:ret]:
    #         f.write(data.to_bytes(1, byteorder="big", signed=True))

    img_path = os.path.join(os.getcwd(), f"image_{int(time.time())}.jpg")
    ret = sdk.save_snap_image_jpeg(handle, img_path)
    print("get_snap_image_jpeg", ret, img_path)

    ret = sdk.set_plate_info_callback(handle, plate_info_callback_result, None, 1)
    print("set_plate_info_callback", ret)

    osd_struct = VZ_LPRC_USER_OSD_EX_PARAM()
    ret = sdk.get_user_osd_param_ex(handle, osd_struct)
    print("get_user_osd_param_ex", ret)

    osd_struct.x_pos = 40
    osd_struct.y_pos = 40
    osd_struct.osd_item[0].front_size = 4
    osd_struct.osd_item[0].color = 1
    osd_struct.osd_item[0].display = 1
    osd_struct.osd_item[0].text = "Python测试文字,多显示一点,再多 more, 哈哈哈".encode("GB2312")
    ret = sdk.set_user_osd_param_ex(handle, osd_struct)
    print("set_user_osd_param_ex", ret)

    ret = sdk.force_trigger(handle)
    print("force trigger", ret == 0)

    ret = sdk.set_whitelist_query_callback(handle, whitelist_query_callback_result, None)
    print("set_whitelist_query_callback", ret)

    plate_data = "川A95273".encode("GB2312")
    whitelist_vehicle = VZ_LPR_WLIST_VEHICLE()
    whitelist_vehicle.strPlateID = plate_data
    whitelist_vehicle.strCode = plate_data
    whitelist_vehicle.uCustomerID = 0
    whitelist_vehicle.bEnable = 1
    struTMOverdule = VZ_TM()
    struTMOverdule.nYear = 2020
    struTMOverdule.nMonth = 12
    struTMOverdule.nMDay = 30
    struTMOverdule.nHour = 12
    struTMOverdule.nMin = 40
    struTMOverdule.nSec = 50
    whitelist_vehicle.struTMOverdule = struTMOverdule
    whitelist_vehicle.bUsingTimeSeg = 0
    whitelist_vehicle.bAlarm = 0
    whitelist_vehicle.bEnableTMOverdule = 1

    wlistRow = VZ_LPR_WLIST_ROW()
    wlistRow.pVehicle = whitelist_vehicle
    wlistRow.pCustomer = None
    importResult = VZ_LPR_WLIST_IMPORT_RESULT()
    ret = sdk.whitelist_import_rows(handle, 1, wlistRow, importResult)
    print("whitelist_import_rows", ret)

    cmd_white_list_insert = "{\"cmd\":\"white_list_operator\",\"dldb_rec\":[{\"context\":\"\",\"enable\":1,\"enable_time\":\"2021-01-28 00:00:00\",\"need_alarm\":0,\"overdue_time\":\"2021-01-28 23:59:59\",\"plate\":\"川A12345\",\"seg_time_end\":\"\",\"seg_time_start\":\"\"},{\"context\":\"\",\"enable\":1,\"enable_time\":\"2021-01-28 00:00:00\",\"need_alarm\":0,\"overdue_time\":\"2021-01-28 23:59:59\",\"plate\":\"川A12346\",\"seg_time_end\":\"\",\"seg_time_start\":\"\"}],\"id\":\"132156\",\"operator_type\":\"update_or_add\"}"
    cmd_type = "white_list_operator"
    response = (c_byte * 1024)()
    ret = sdk.tcp_trans_send(handle, cmd_type, cmd_white_list_insert, response, 1024)
    print("tcp_trans_send", ret)
    print(''.join([chr(i) for i in response]).strip('\x00'))

    ret = sdk.whitelist_delete_vehicle(handle, "川A12345")
    print("whitelist_delete_vehicle", ret)

    ret = sdk.whitelist_query_vehicle_by_plate(handle, "A12346")
    print("whitelist_query_vehicle_by_plate", ret)

    pDTInfo = VZ_DATE_TIME_INFO()
    pDTInfo.uYear = 2019
    pDTInfo.nMonth = 5
    pDTInfo.nMDay = 6
    pDTInfo.nHour = 12
    pDTInfo.nMin = 30
    pDTInfo.nSec = 40
    ret = sdk.set_date_time(handle, pDTInfo)
    print("set_date_time", ret)

    serial_handle = sdk.serial_start(handle, 0, serial_recv_callback_result, None)
    print("serial_start", serial_handle)
    serial_data = "1234567".encode("utf-8")
    ret = sdk.serial_send(serial_handle, serial_data, len(serial_data))
    print("serial_send", ret)
    ret = sdk.serial_stop(serial_handle)
    print("serial_stop", ret)

    ret = sdk.start_save_realdata(handle, os.path.join(os.getcwd(), "test.avi"))
    print("start_save_realdata", ret)
    time.sleep(5)
    ret = sdk.stop_save_realdata(handle)
    print("stop_save_realdata", ret)

    ret = sdk.close(handle)
    print("close device", ret == 0)

    ret = sdk.cleanup()
    print("cleanup", ret == 0)
