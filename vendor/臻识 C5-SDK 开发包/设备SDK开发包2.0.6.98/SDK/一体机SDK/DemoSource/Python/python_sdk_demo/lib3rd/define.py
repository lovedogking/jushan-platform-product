#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from ctypes import *


class VZ_LPRC_IMAGE_INFO(Structure):
    _fields_ = [('uWidth', c_int), ('uHeight', c_int), ('uPitch', c_int), ('uPixFmt', c_int), ('pBuffer', POINTER(c_ubyte))]


class VZ_TM(Structure):
    _fields_ = [('nYear', c_short), ('nMonth', c_short), ('nMDay', c_short), ('nHour', c_short), ('nMin', c_short), ('nSec', c_short)]


class VZ_DATE_TIME_INFO(Structure):
    _fields_ = [('nYear', c_int), ('nMonth', c_int), ('nMDay', c_int), ('nHour', c_int), ('nMin', c_int), ('nSec', c_int)]


class VZ_LPR_WLIST_VEHICLE(Structure):
    _fields_ = [('uVehicleID', c_int), ('strPlateID', c_char * 32), ('uCustomerID', c_int), ('bEnable', c_int), ('bEnableTMEnable', c_int), ('bEnableTMOverdule', c_int),
                ('struTMEnable', VZ_TM), ('struTMOverdule', VZ_TM), ('bUsingTimeSeg', c_int), ('byTimeSegOrRange', c_char * 228), ('bAlarm', c_int), ('iColor', c_int),
                ('iPlateType', c_int), ('strCode', c_char * 32), ('strComment', c_char * 64)]


class VZ_LPR_WLIST_ROW(Structure):
    _fields_ = [('pCustomer', POINTER(c_int)), ('pVehicle', VZ_LPR_WLIST_VEHICLE)]


class VZ_LPR_WLIST_CUSTOMER(Structure):
    _fields_ = [('uCustomerID', c_int), ('strName', c_char * 32), ('strCode', c_char * 32), ('reserved', c_char * 256)]


class VZ_LPR_WLIST_IMPORT_RESULT(Structure):
    _fields_ = [('ret', c_int), ('error_code', c_int)]


class VZ_LPR_WLIST_RANGE_LIMIT(Structure):
    _fields_ = [('startIndex', c_int), ('count', c_int)]


class VZ_LPR_WLIST_LIMIT(Structure):
    _fields_ = [('limitType', c_int), ('pRangeLimit', VZ_LPR_WLIST_RANGE_LIMIT)]


class VZ_LPR_WLIST_SEARCH_CONSTRAINT(Structure):
    _fields_ = [('key', c_char * 32), ('search_string', c_char * 128)]


class VZ_LPR_WLIST_SEARCH_WHERE(Structure):
    _fields_ = [('searchType', c_int), ('searchConstraintCount', c_int)]


class VZ_LPR_WLIST_LOAD_CONDITIONS(Structure):
    _fields_ = [('pSearchWhere', VZ_LPR_WLIST_SEARCH_WHERE), ('pLimit', VZ_LPR_WLIST_LIMIT), ('pSortType', POINTER(c_ubyte))]


class VZ_LPRC_VERTEX(Structure):
    _fields_ = [('X_1000', c_int), ('Y_1000', c_int)]


class VZ_LPRC_VIRTUAL_LOOP_EX(Structure):
    _fields_ = [('byID', c_char), ('byEnable', c_char), ('byDraw', c_char), ('byRes', c_char), ('strName', c_char * 32),
                ('uNumVertex', c_int), ('struVertex', VZ_LPRC_VERTEX * 12), ('eCrossDir', c_int), ('uTriggerTimeGap', c_int), ('uMaxLPWidth', c_int), ('uMinLPWidth', c_int)]


class VZ_LPRC_VIRTUAL_LOOPS_EX(Structure):
    _fields_ = [('uNumVirtualLoop', c_int), ('struLoop', VZ_LPRC_VIRTUAL_LOOP_EX * 8)]


class VZ_LPRC_USER_OSD_ITEM_PARAM(Structure):
    _fields_ = [('id', c_char), ('display', c_char), ('color', c_char), ('front_size', c_char), ('text', c_char * 64)]


class VZ_LPRC_USER_OSD_EX_PARAM(Structure):
    _fields_ = [('x_pos', c_int), ('y_pos', c_int), ('osd_item', VZ_LPRC_USER_OSD_ITEM_PARAM * 4)]


class CarBrand(Structure):
    _fields_ = [('brand', c_char), ('type', c_char), ('year', c_ushort)]


class TH_RECT(Structure):
    _fields_ = [('left', c_int), ('top', c_int), ('right', c_int), ('bottom', c_int)]


class VZ_TIMEVAL(Structure):
    _fields_ = [('uTVSec', c_int), ('uTVUSec', c_int)]


class VzBDTime(Structure):
    _fields_ = [('bdt_sec', c_char), ('bdt_min', c_char), ('bdt_hour', c_char), ('bdt_mday', c_char),
                ('bdt_mon', c_char), ('res1', c_char * 3), ('bdt_year', c_int), ('res2', c_char * 4)]


class VZ_LPRC_OSD_Param(Structure):
    _fields_ = [('dstampenable', c_char), ('dateFormat', c_int), ('datePosX', c_int), ('datePosY', c_int),
                ('tstampenable', c_char), ('timeFormat', c_int), ('timePosX', c_int), ('timePosY', c_int),
                ('nLogoEnable', c_char), ('nLogoPositionX', c_int), ('nLogoPositionY', c_int), ('nTextEnable', c_char),
                ('nTextPositionX', c_int), ('nTextPositionY', c_int), ('overlaytext', c_char * 16)]


class TH_PlateResult(Structure):
    _fields_ = [('license', c_char * 16), ('color', c_char * 8), ('nColor', c_int), ('nType', c_int),
                ('nConfidence', c_int), ('nBright', c_int), ('nDirection', c_int), ('rcLocation', TH_RECT),
                ('nTime', c_int), ('tvPTS', VZ_TIMEVAL), ('uBitsTrigType', c_int), ('nCarBright', c_char),
                ('nCarColor', c_char), ('reserved0', c_char * 2), ('uId', c_uint), ('struBDTime', VzBDTime),
                ('nIsEncrypt', c_char), ('nPlateTrueWidth', c_char), ('nPlateDistance', c_char), ('nIsFakePlate', c_char),
                ('car_location', TH_RECT), ('car_brand', CarBrand), ('featureCode', c_char * 20), ('reserved1', c_char * 24)]


class VZ_SCENE_POS(Structure):
    _fields_ = [('x', c_int), ('y', c_int), ('width', c_int), ('height', c_int)]


class VZ_SCENE_RES_ELEM(Structure):
    _fields_ = [('elem_id', c_int), ('elem_enable', c_int), ('elem_name', c_char * 32), ('elem_type', c_char * 16), ('elem_sub_type', c_char * 32),
                ('picture_max_duration', c_int), ('video_play_count', c_int), ('res_name', c_char * 96), ('pos', VZ_SCENE_POS), ('dwRes', c_char * 256)]


class VZ_SCENE_TEXT_ELEM(Structure):
    _fields_ = [('elem_id', c_int), ('elem_enable', c_int), ('elem_name', c_char * 32), ('elem_type', c_char * 16), ('elem_sub_type', c_char * 32),
                ('elem_text_content', c_char * 96), ('elem_bg_color', c_char * 32), ('elem_fg_color', c_char * 32),
                ('elem_font_family', c_int), ('elem_font_bold', c_int), ('elem_font_size', c_int), ('elem_align', c_char * 16), ('extern_text', c_char * 96),
                ('extern_text_enable', c_int), ('pos', VZ_SCENE_POS), ('res_name', c_char * 96), ('dwRes', c_char * 256)]


class VZ_SCENE_INFO(Structure):
    _fields_ = [('scene_name', c_char * 64), ('scene_id', c_int), ('scene_mode', c_int), ('scene_type', c_char * 32),
                ('scene_max_duration', c_int), ('video_play_count', c_int), ('background_type', c_int), ('background_color', c_char * 32),
                ('background_image', c_char * 96), ('pos', VZ_SCENE_POS), ('res_elem', VZ_SCENE_RES_ELEM * 10), ('res_elem_count', c_int),
                ('text_elem', VZ_SCENE_TEXT_ELEM * 10), ('text_elem_count', c_int), ('dwRes', c_char * 256)]


class VZ_AD_CONFIG(Structure):
    _fields_ = [('left', VZ_SCENE_INFO * 5), ('scene_count', c_int), ('ad_type', c_int), ('ratio', c_int)]

