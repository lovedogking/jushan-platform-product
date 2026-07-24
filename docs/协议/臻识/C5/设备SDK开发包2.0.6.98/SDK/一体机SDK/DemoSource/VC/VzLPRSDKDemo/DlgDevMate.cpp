// DlgDevMate.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgDevMate.h"


// DlgDevMate dialog

IMPLEMENT_DYNAMIC(DlgDevMate, CDialog)

DlgDevMate::DlgDevMate(CWnd* pParent /*=NULL*/)
	: CDialog(DlgDevMate::IDD, pParent)
{
	play_handle_ = 0;
}

DlgDevMate::~DlgDevMate()
{
}

void DlgDevMate::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_LIST_RG_DEV, rg_dev_list_);
	DDX_Control(pDX, IDC_PLAY_WND, play_wnd_);
	DDX_Control(pDX, IDC_BTN_PLAY, play_btn_);
	DDX_Control(pDX, IDC_BTN_TALK, talk_button_);
}


BEGIN_MESSAGE_MAP(DlgDevMate, CDialog)
	ON_BN_CLICKED(IDC_BTN_PLAY, &DlgDevMate::OnBnClickedBtnPlay)
	ON_BN_CLICKED(IDC_BTN_TALK, &DlgDevMate::OnBnClickedBtnTalk)
	ON_WM_CLOSE()
END_MESSAGE_MAP()


// DlgDevMate message handlers

BOOL DlgDevMate::OnInitDialog()
{
	CDialog::OnInitDialog();

	rg_dev_list_.InsertColumn( 0, "设备IP",   LVCFMT_LEFT,   90);
	rg_dev_list_.InsertColumn( 1, "序列号",   LVCFMT_LEFT,   115);
	rg_dev_list_.InsertColumn( 2, "在线状态",   LVCFMT_LEFT, 65);
	rg_dev_list_.SetExtendedStyle(rg_dev_list_.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);

	VZ_LPRC_MATE_INFO mate_info = { 0 };
	int ret = VzLPRClient_GetMateInfo(lpr_handle_, &mate_info);

	for (int i = 0; i < mate_info.mate_count; i++) { 
		int nIndex = rg_dev_list_.InsertItem(0, mate_info.mate_items[i].dev_ip);
		rg_dev_list_.SetItemText(nIndex, 1, mate_info.mate_items[i].sn);

		if (mate_info.mate_items[i].dev_online == 1) {
			rg_dev_list_.SetItemText(nIndex, 2, "在线");
		}
		else {
			rg_dev_list_.SetItemText(nIndex, 2, "离线");
		}
	}

	return TRUE;
}

void DlgDevMate::OnBnClickedBtnPlay()
{
	if(rg_dev_list_.GetItemCount() == 0) {
		MessageBox("当前无伴侣设备!");
		return;
	}

	CString btn_text;
	play_btn_.GetWindowText(btn_text);
	if (btn_text == "停止播放") {
		VzLPRClient_StopRealPlay(play_handle_);
		play_wnd_.ShowWindow(SW_HIDE);
		play_btn_.SetWindowText("播放伴侣视频");
		play_handle_ = 0;
		play_wnd_.ShowWindow(SW_SHOW);
		return;
	}

	int cur_sel = iListCtrlGetCurSel(&rg_dev_list_);
	if (cur_sel < 0) {
		cur_sel = 0;
	}

	CString mate_sn;
	mate_sn = rg_dev_list_.GetItemText(cur_sel,1);

	VZ_LPRC_RTSP_INFO rtsp_info = { 0 };
	int ret = VzLPRClient_GetRtspInfo(lpr_handle_, mate_sn.GetBuffer(0), &rtsp_info);
	if (ret == 0) {
		play_handle_ = VzLPRClient_StartPlayUrl(lpr_handle_, rtsp_info.rtsp_proxyurl, play_wnd_.GetSafeHwnd());
		if (play_handle_ != 0) {
			play_btn_.SetWindowText("停止播放");
		}
	}
	else {
		MessageBox("获取RTSP代理信息失败!");
		return;
	}
}


int DlgDevMate::iListCtrlGetCurSel(CListCtrl *pList)
{
	int nCurrSel = -1;
	POSITION pos = pList->GetFirstSelectedItemPosition();
	while(pos)
	{
		nCurrSel = pList->GetNextSelectedItem(pos);
	}

	return(nCurrSel);
}
void DlgDevMate::OnBnClickedBtnTalk()
{
	if(rg_dev_list_.GetItemCount() == 0) {
		MessageBox("当前无伴侣设备!");
		return;
	}

	CString btn_text;
	talk_button_.GetWindowText(btn_text);
	if (btn_text == "停止对讲") {
		VzLPRClient_StopTalk(lpr_handle_);
		talk_button_.SetWindowText("开始对讲");
		return;
	}

	int cur_sel = iListCtrlGetCurSel(&rg_dev_list_);
	if (cur_sel < 0) {
		cur_sel = 0;
	}

	CString mate_sn;
	mate_sn = rg_dev_list_.GetItemText(cur_sel,1);

	VZ_LPRC_REQUEST_TALK_INFO talk_info = { 0 };
	int ret = VzLPRClient_GetRequestTalkInfo(lpr_handle_, mate_sn.GetBuffer(0), 640, &talk_info);
	if (ret == 0) {
		int ret_talk = VzLPRClient_StartTalkProxy(lpr_handle_, talk_info.window_size, talk_info.encode_type, talk_info.talk_port, talk_info.sampling_rate, talk_info.sample_point);
		if (ret_talk == 0) {
			talk_button_.SetWindowText("停止对讲");
		}
	}
	else {
		MessageBox("获取对讲代理信息失败!");
		return;
	}
}

void DlgDevMate::OnClose()
{
	// TODO: Add your message handler code here and/or call default
	if (play_handle_ != 0) {
		VzLPRClient_StopRealPlay(play_handle_);
	}

	VzLPRClient_StopTalk(lpr_handle_);
	

	CDialog::OnClose();
}
