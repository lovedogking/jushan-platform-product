#!/usr/bin/env python3
# -*- coding: GB2312 -*-
import os
import win32api
from lib3rd.define import *
from ctypes import *

path = os.path.realpath(os.path.join(os.getcwd(), "lib3rd/vzsdk_win64"))
print("Add environ path ->", path)
os.environ['path'] += f';{path}'


class VzSdkCallback:
    sdk_plate_info_callback = None
    custom_plate_info_callback = None

    sdk_serial_recv_callback = None
    custom_serial_recv_callback = None

    sdk_common_notify_callback = None
    custom_common_notify_callback = None

    sdk_find_device_callback = None
    custom_find_device_callback = None

    sdk_whitelist_query_callback = None
    custom_whitelist_query_callback = None

    @staticmethod
    @CFUNCTYPE(None, c_int, c_void_p, POINTER(TH_PlateResult), c_uint, c_int, POINTER(VZ_LPRC_IMAGE_INFO),
               POINTER(VZ_LPRC_IMAGE_INFO))
    def VZLPRC_PLATE_INFO_CALLBACK(sdk_hdl, user_data, plate_result, plate_num, result_type, img_full,
                                   img_plate_clip=None):
        """
        通过该回调函数获得车牌识别信息
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :param user_data:回调函数的上下文
        :param plate_result:车牌信息数组首地址，详见结构体定义 TH_PlateResult
        :param plate_num:车牌数组中的车牌个数
        :param result_type:车牌识别结果类型，详见枚举类型定义VZ_LPRC_RESULT_TYPE
        :param img_full:当前帧的图像内容，详见结构体定义VZ_LPRC_IMAGE_INFO
        :param img_plate_clip:当前帧中车牌区域的图像内容数组，其中的元素与车牌信息数组中的对应
        :return:0表示成功，-1表示失败
        """
        print("VZLPRC_PLATE_INFO_CALLBACK", sdk_hdl, user_data, plate_result, plate_num, result_type, img_full,
              img_plate_clip)
        print(plate_result.contents.uId, plate_result.contents.nIsEncrypt, plate_result.contents.tvPTS.uTVSec)
        try:
            print("license", plate_result.contents.license.decode("GB2312"))
        except Exception as e:
            print("license decode exception", e)
        print(img_full.contents.uWidth, img_full.contents.uHeight)
        print(img_plate_clip.contents.uWidth, img_plate_clip.contents.uHeight)
        if VzSdkCallback.custom_plate_info_callback:
            VzSdkCallback.custom_plate_info_callback(sdk_hdl, user_data, plate_result, plate_num, result_type, img_full,
                                                     img_plate_clip)
        pass

    @staticmethod
    @CFUNCTYPE(None, c_int, c_void_p, c_int, c_void_p)
    def VZDEV_SERIAL_RECV_DATA_CALLBACK(sdk_hdl, recv_data, recv_size, user_data):
        """
        通过该回调函数获得透明通道接收的数据
        :param sdk_hdl:VzLPRClient_SerialStart返回的句柄
        :param recv_data:接收的数据的首地址
        :param recv_size:接收的数据的尺寸
        :param user_data:回调函数上下文
        :return:
        """
        print("VZDEV_SERIAL_RECV_DATA_CALLBACK", sdk_hdl, recv_data, recv_size, user_data)
        if VzSdkCallback.custom_serial_recv_callback:
            VzSdkCallback.custom_serial_recv_callback(sdk_hdl, recv_data, recv_size, user_data)
        pass

    @staticmethod
    @CFUNCTYPE(None, c_int, c_void_p, c_int, c_wchar_p)
    def VZLPRC_COMMON_NOTIFY_CALLBACK(sdk_hdl, user_data, notify, detail):
        """
        通过该回调函数获得设备的一般状态信息
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :param user_data:回调函数上下文
        :param notify:通用信息反馈类型
        :param detail:详细描述字符串
        :return:
        """
        print("VZLPRC_COMMON_NOTIFY_CALLBACK", sdk_hdl, user_data, notify, detail)
        if VzSdkCallback.custom_common_notify_callback:
            VzSdkCallback.custom_common_notify_callback(sdk_hdl, user_data, notify, detail)
        pass

    @staticmethod
    @CFUNCTYPE(None, c_wchar_p, c_wchar_p, c_short, c_short, c_long, c_long, c_wchar_p, c_wchar_p, c_void_p)
    def VZLPRC_FIND_DEVICE_CALLBACK_EX(dev_name, ip_addr, us_port, us_type, sl, sh, netmask, gateway, user_data):
        """
        通过该回调函数获得找到的设备基本信息
        :param dev_name:设备名称
        :param ip_addr:设备IP地址
        :param us_port:设备端口号
        :param us_type:设备类型(预留)
        :param sl:设备序列号低位字节
        :param sh:设备序列号高位字节
        :param netmask:子网掩码
        :param gateway:网关回调函数上下文
        :param user_data:回调函数上下文
        :return:
        """
        print("VZLPRC_FIND_DEVICE_CALLBACK_EX", dev_name, ip_addr, us_port, us_type, sl, sh, netmask, gateway,
              user_data)
        if VzSdkCallback.custom_find_device_callback:
            VzSdkCallback.custom_find_device_callback(dev_name, ip_addr, us_port, us_type, sl, sh, netmask, gateway,
                                                      user_data)
        pass

    @staticmethod
    @CFUNCTYPE(None, c_int, POINTER(VZ_LPR_WLIST_VEHICLE), POINTER(VZ_LPR_WLIST_CUSTOMER), c_void_p)
    def VZLPRC_WLIST_QUERY_CALLBACK(cbtype, vehicle, customer, user_data):
        """
        通过该回调函数获得设备白名单操作信息
        :param cbtype:
        :param vehicle:
        :param customer:
        :param user_data:
        :return:
        """
        print("VZLPRC_WLIST_QUERY_CALLBACK", cbtype, vehicle, customer, user_data)
        if VzSdkCallback.custom_whitelist_query_callback:
            VzSdkCallback.custom_whitelist_query_callback(cbtype, vehicle, customer, user_data)
        pass


class VzLprSDK:
    def __init__(self):
        # 加载VZSDK
        self.objdll = cdll.LoadLibrary("./lib3rd/vzsdk_win64/VzLPRSDK.dll")

    def __del__(self):
        # 释放VZSDK
        win32api.FreeLibrary(self.objdll._handle)

    def setup(self):
        """
        全局初始化
        :return: 0表示成功，-1表示失败
        """
        return self.objdll.VzLPRClient_Setup()

    def cleanup(self):
        """
        全局释放
        :return: 0表示成功，-1表示失败
        """
        return self.objdll.VzLPRClient_Cleanup()

    def open(self, ip, port=80, username="admin", password="admin"):
        """
        打开一个设备
        :param ip: 设备的IP地址
        :param port: 设备的端口号
        :param username: 访问设备所需用户名
        :param password: 访问设备所需密码
        :return sdk_hdl:设备的操作句柄，当打开失败时，返回0
        """
        return self.objdll.VzLPRClient_Open(ip.encode('GB2312'), port, username.encode('GB2312'),
                                            password.encode('GB2312'))

    def close(self, sdk_hdl):
        """
        关闭一个设备
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :return:0表示成功，-1表示失败
        """
        return self.objdll.VzLPRClient_Close(sdk_hdl)

    def is_connected(self, sdk_hdl, state):
        """
        获取连接状态
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :param state:获取状态的变量地址，输出内容为 1已连上，0未连上
        :return:0表示成功，-1表示失败
        """
        return self.objdll.VzLPRClient_IsConnected(sdk_hdl, state)

    def force_trigger(self, sdk_hdl):
        """
        发送软件触发信号，强制处理当前时刻的数据并输出结果
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :return:0表示成功，-1表示失败
        """
        return self.objdll.VzLPRClient_ForceTrigger(sdk_hdl)

    def get_device_ip(self, sdk_hdl, ip, max_count):
        return self.objdll.VzLPRClient_GetDeviceIP(sdk_hdl, ip, max_count)

    def get_snap_image(self, sdk_hdl, img_dst_buf, img_len):
        """
        保存抓图数据到缓冲区
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :param img_dst_buf:接收图片的缓冲区
        :param img_len:接收图片的缓冲区长度
        :return:返回值为大于0表示接收图片的长度，返回其他值表示失败。
        """
        return self.objdll.VzLPRClient_GetSnapImage(sdk_hdl, img_dst_buf, img_len)

    def save_snap_image_jpeg(self, sdk_hdl, img_path):
        """
        保存抓图数据到Jpeg文件，该操作会触发手动触发
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :param img_path:图片路径
        :return:返回值为0表示成功，返回其他值表示失败。
        """
        return self.objdll.VzLPRClient_SaveSnapImageToJpeg(sdk_hdl, img_path.encode('GB2312'))

    def image_save_to_jpeg(self, image_info, full_path, quality):
        return self.objdll.VzLPRClient_ImageSaveToJpeg(image_info, full_path, quality)

    def load_image_by_id(self, sdk_hdl, img_id, img_data, img_len):
        return self.objdll.VzLPRClient_LoadImageById(sdk_hdl, img_id, img_data, img_len)

    def set_plate_info_callback(self, sdk_hdl, plate_info_callback, user_data, enable_image):
        """
        设置识别结果的回调函数
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :param plate_info_callback:识别结果回调函数，如果为NULL，则表示关闭该回调函数的功能
        :param user_data:回调函数中的上下文
        :param enable_image:指定识别结果的回调是否需要包含截图信息：1为需要，0为不需要
        :return:0表示成功，-1表示失败
        """
        if plate_info_callback is not None:
            VzSdkCallback.sdk_plate_info_callback = VzSdkCallback.VZLPRC_PLATE_INFO_CALLBACK
            VzSdkCallback.custom_plate_info_callback = plate_info_callback
        else:
            VzSdkCallback.sdk_plate_info_callback = None
            VzSdkCallback.custom_plate_info_callback = None
        return self.objdll.VzLPRClient_SetPlateInfoCallBack(sdk_hdl, VzSdkCallback.sdk_plate_info_callback, user_data,
                                                            enable_image)

    def serial_start(self, sdk_hdl, serial_port, serial_recv_callback, userdata):
        """
        开启透明通道(未测试)
        :param sdk_hdl:由VzLPRClient_Open函数获得的句柄
        :param serial_port:指定使用设备的串口序号：0表示第一个串口，1表示第二个串口
        :param serial_recv_callback:接收数据的回调函数
        :param userdata:接收数据回调函数的上下文
        :return:返回透明通道句柄，0表示失败
        """
        if serial_recv_callback is not None:
            VzSdkCallback.sdk_serial_recv_callback = VzSdkCallback.VZDEV_SERIAL_RECV_DATA_CALLBACK
            VzSdkCallback.custom_serial_recv_callback = serial_recv_callback
        else:
            VzSdkCallback.sdk_plate_info_callback = None
            VzSdkCallback.custom_plate_info_callback = None
        return self.objdll.VzLPRClient_SerialStart(sdk_hdl, serial_port, VzSdkCallback.sdk_serial_recv_callback,
                                                   userdata)

    def serial_send(self, serial_hdl, data, size):
        """
        透明通道发送数据
        :param serial_hdl:由VzLPRClient_SerialStart函数获得的句柄
        :param data:将要传输的数据块的首地址
        :param size:将要传输的数据块的字节数
        :return:0表示成功，其他值表示失败
        """
        return self.objdll.VzLPRClient_SerialSend(serial_hdl, data, size)

    def serial_stop(self, serial_hdl):
        """
        透明通道停止发送数据
        :param serial_hdl:由VzLPRClient_SerialStart函数获得的句柄
        :return:0表示成功，其他值表示失败
        """
        return self.objdll.VzLPRClient_SerialStop(serial_hdl)

    def tcp_trans_send(self, sdk_hdl, cmd_type, cmd, response, max_response_len):
        return self.objdll.VzLPRClient_TcpTransSend(sdk_hdl, cmd_type.encode("GB2312"), cmd.encode("GB2312"), response, max_response_len)

    def get_gpio_value(self, sdk_hdl, gpio_in, value):
        return self.objdll.VzLPRClient_GetGPIOValue(sdk_hdl, gpio_in, value)

    def set_io_output_auto(self, sdk_hdl, chnId, duration):
        """

        :param sdk_hdl:
        :param chnId:
        :param duration:
        :return:
        """
        return self.objdll.VzLPRClient_SetIOOutputAuto(sdk_hdl, chnId, duration)

    def set_date_time(self, sdk_hdl, dt_info_param):
        return self.objdll.VzLPRClient_SetDateTime(sdk_hdl, dt_info_param)

    def get_date_time(self, sdk_hdl, dt_info_param):
        # return self.objdll.VzLPRClient_GetDateTime(sdk_hdl, dt_info_param)
        pass

    def set_virtual_loop_ex(self, sdk_hdl, virtual_loops_param):
        return self.objdll.VzLPRClient_SetVirtualLoopEx(sdk_hdl, virtual_loops_param)

    def get_virtual_loop_ex(self, sdk_hdl, virtual_loops_param):
        return self.objdll.VzLPRClient_GetVirtualLoopEx(sdk_hdl, virtual_loops_param)

    def set_osd_param(self, sdk_hdl, osd_param):
        """

        :param sdk_hdl:
        :param osd_param:
        :return:
        """
        return self.objdll.VzLPRClient_SetOsdParam(sdk_hdl, osd_param)

    def get_osd_param(self, sdk_hdl, osd_param):
        """

        :param sdk_hdl:
        :param osd_param:
        :return:
        """
        return self.objdll.VzLPRClient_GetOsdParam(sdk_hdl, osd_param)

    def set_user_osd_param_ex(self, sdk_hdl, osd_param):
        return self.objdll.VzLPRClient_SetUserOsdParamEx(sdk_hdl, osd_param)

    def get_user_osd_param_ex(self, sdk_hdl, osd_param):
        return self.objdll.VzLPRClient_GetUserOsdParamEx(sdk_hdl, osd_param)

    def get_lcd_ad_config(self, sdk_hdl, ad_config_param):
        return self.objdll.VzLPRClient_GetLcdAdConfig(sdk_hdl, ad_config_param)

    def set_lcd_ad_config(self, sdk_hdl, ad_type, ad_config_param):
        return self.objdll.VzLPRClient_SetLcdAdConfig(sdk_hdl, ad_type, ad_config_param)

    def set_ad_scene_elem_item(self, sdk_hdl, ad_config_param, scene_name, elem_id, key, value):
        return self.objdll.VzLPRClient_SetAdSceneElemItem(sdk_hdl, ad_config_param, scene_name, elem_id, key, value)

    def set_ad_scene_item(self, sdk_hdl, ad_config_param, scene_name, key, value):
        return self.objdll.VzLPRClient_SetAdSceneItem(sdk_hdl, ad_config_param, scene_name, key, value)

    def set_whitelist_query_callback(self, sdk_hdl, whitelist_query_callback, user_data):
        if whitelist_query_callback is not None:
            VzSdkCallback.sdk_whitelist_query_callback = VzSdkCallback.VZLPRC_WLIST_QUERY_CALLBACK
            VzSdkCallback.custom_whitelist_query_callback = whitelist_query_callback
        else:
            VzSdkCallback.sdk_whitelist_query_callback = None
            VzSdkCallback.custom_whitelist_query_callback = None
        return self.objdll.VzLPRClient_WhiteListSetQueryCallBack(sdk_hdl, VzSdkCallback.sdk_whitelist_query_callback,
                                                                 user_data)

    def whitelist_import_rows(self, sdk_hdl, rowcount, row_data_param, result_param):
        return self.objdll.VzLPRClient_WhiteListImportRows(sdk_hdl, rowcount, row_data_param, result_param)

    def whitelist_insert_vehicle(self, sdk_hdl, vehicle_param):
        return self.objdll.VzLPRClient_WhiteListInsertVehicle(sdk_hdl, vehicle_param)

    def whitelist_clear_customers_and_vehicles(self, sdk_hdl):
        return self.objdll.VzLPRClient_WhiteListClearCustomersAndVehicles(sdk_hdl)

    def whitelist_delete_vehicle(self, sdk_hdl, plate_id):
        return self.objdll.VzLPRClient_WhiteListDeleteVehicle(sdk_hdl, plate_id.encode("GB2312"))

    def whitelist_query_vehicle_by_plate(self, sdk_hdl, plate_id):
        return self.objdll.VzLPRClient_WhiteListQueryVehicleByPlate(sdk_hdl, plate_id.encode("GB2312"))

    def whitelist_get_vehicle_count(self, sdk_hdl, count_list, search_where_param):
        return self.objdll.VzLPRClient_WhiteListGetVehicleCount(sdk_hdl, count_list, search_where_param)

    def whitelist_load_vehicle(self, sdk_hdl, load_condition_param):
        return self.objdll.VzLPRClient_WhiteListLoadVehicle(sdk_hdl, load_condition_param)

    def start_save_realdata(self, sdk_hdl, file_name):
        return self.objdll.VzLPRClient_SaveRealData(sdk_hdl, file_name.encode("GB2312"))

    def stop_save_realdata(self, sdk_hdl):
        return self.objdll.VzLPRClient_StopSaveRealData(sdk_hdl)

    def set_common_notify_callback(self, common_notify_callback, user_data):
        if common_notify_callback is not None:
            VzSdkCallback.sdk_common_notify_callback = VzSdkCallback.VZLPRC_COMMON_NOTIFY_CALLBACK
            VzSdkCallback.custom_common_notify_callback = common_notify_callback
        else:
            VzSdkCallback.sdk_common_notify_callback = None
            VzSdkCallback.custom_common_notify_callback = None
        return self.objdll.VZLPRClient_SetCommonNotifyCallBack(VzSdkCallback.sdk_common_notify_callback, user_data)

    def start_find_device_ex(self, find_device_callback, user_data):
        if find_device_callback is not None:
            VzSdkCallback.sdk_find_device_callback = VzSdkCallback.VZLPRC_FIND_DEVICE_CALLBACK_EX
            VzSdkCallback.custom_find_device_callback = find_device_callback
        else:
            VzSdkCallback.sdk_find_device_callback = None
            VzSdkCallback.custom_find_device_callback = None
        return self.objdll.VZLPRClient_StartFindDeviceEx(VzSdkCallback.sdk_find_device_callback, user_data)

    def stop_find_device(self):
        return self.objdll.VZLPRClient_StopFindDevice()


lpr_sdk = VzLprSDK()
