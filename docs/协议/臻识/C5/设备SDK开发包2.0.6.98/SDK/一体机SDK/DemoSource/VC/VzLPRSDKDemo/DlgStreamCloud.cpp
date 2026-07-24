// DlgStreamCloud.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgStreamCloud.h"


// CDlgStreamCloud dialog

IMPLEMENT_DYNAMIC(CDlgStreamCloud, CDialog)

CDlgStreamCloud::CDlgStreamCloud(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgStreamCloud::IDD, pParent)
{
	m_dtStart = COleDateTime::GetCurrentTime();
	m_dtStartTime = COleDateTime::GetCurrentTime();
	m_dtEnd = COleDateTime::GetCurrentTime();
	m_dtEndTime = COleDateTime::GetCurrentTime();

	m_hLPRClient = NULL;
	m_play_handle = 0;
	m_select_index = -1;
}

CDlgStreamCloud::~CDlgStreamCloud()
{
}

void CDlgStreamCloud::DoDataExchange(CDataExchange* pDX)
{
	DDX_DateTimeCtrl(pDX, IDC_DT_START, m_dtStart);
	DDX_DateTimeCtrl(pDX, IDC_DT_START_TIME, m_dtStartTime);
	DDX_DateTimeCtrl(pDX, IDC_DT_END, m_dtEnd);
	DDX_DateTimeCtrl(pDX, IDC_DT_END_TIME, m_dtEndTime);
	DDX_Control(pDX, IDC_LIST_PLATE, m_lstRecordFile);
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_STATIC_SHOW_QUERY, m_play_wnd);
}


BEGIN_MESSAGE_MAP(CDlgStreamCloud, CDialog)
	ON_BN_CLICKED(IDC_BTN_QUERY, &CDlgStreamCloud::OnBnClickedBtnQuery)
	ON_BN_CLICKED(IDC_BTN_PLAY, &CDlgStreamCloud::OnBnClickedBtnPlay)
	ON_NOTIFY(LVN_ITEMCHANGED, IDC_LIST_PLATE, &CDlgStreamCloud::OnLvnItemchangedListPlate)
	ON_WM_CLOSE()
END_MESSAGE_MAP()


// CDlgStreamCloud message handlers

BOOL CDlgStreamCloud::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_lstRecordFile.InsertColumn(0, "记录ID",   LVCFMT_LEFT,   60);
	m_lstRecordFile.InsertColumn(1, "起始时间",   LVCFMT_LEFT,   120);
	m_lstRecordFile.InsertColumn(2, "文件名称",   LVCFMT_LEFT,   280);

	m_lstRecordFile.SetExtendedStyle(m_lstRecordFile.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);

	m_dtStartTime.SetTime(0,0,0);
	m_dtEndTime.SetTime(23,59, 59);
	UpdateData(FALSE);

	return TRUE;
}

void CDlgStreamCloud::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgStreamCloud::OnBnClickedBtnQuery()
{
	m_select_index = -1;
	m_lstRecordFile.DeleteAllItems( );

	if (m_hLPRClient == NULL) {
		return;
	}

	UpdateData(TRUE);

	char szStatTime[128] = {0};
	sprintf_s( szStatTime, sizeof(szStatTime), "%d-%02d-%02d %02d:%02d:%02d", m_dtStart.GetYear(), m_dtStart.GetMonth(), m_dtStart.GetDay(),
		m_dtStartTime.GetHour(), m_dtStartTime.GetMinute(), m_dtStartTime.GetSecond() );

	char szEndTime[128]  = {0};
	sprintf_s(szEndTime, sizeof(szEndTime), "%d-%02d-%02d %02d:%02d:%02d", m_dtEnd.GetYear(), m_dtEnd.GetMonth(), m_dtEnd.GetDay(),
		m_dtEndTime.GetHour(), m_dtEndTime.GetMinute(), m_dtEndTime.GetSecond() );

	VzStream_RecordList *record_list = new VzStream_RecordList;
	memset(record_list, 0, sizeof(VzStream_RecordList));

	VzStream_RecordQueryInfo rec_query_info = {0};
	strcpy(rec_query_info.start_time,szStatTime);
	strcpy(rec_query_info.end_time,szEndTime);
	rec_query_info.query_size = 128;
	rec_query_info.type = 99;
	int ret_query = VzClient_QueryCloudRecordList(m_hLPRClient, &rec_query_info, record_list);
	if (record_list->total > 0) {

		for (int i = 0; i < record_list->total; i++) {
			CString strID;
			strID.Format("%d", i + 1);
			int nIndex = m_lstRecordFile.InsertItem(i, strID);

			char buf[128]= {0};    
			time_t t = record_list->records[i].start_time; 
			tm* local = localtime(&t);
			strftime(buf, 64, "%H:%M:%S", local);

			char buf_end[128]= {0};    
			time_t t_end = record_list->records[i].end_time; 
			tm* local_end = localtime(&t);
			strftime(buf_end, 64, "%H:%M:%S", local_end);

			CString time;
			// time.Format("%s - %s", buf, buf_end);
			time.Format("%s", buf);
			m_lstRecordFile.SetItemText(nIndex, 1, time);

			m_lstRecordFile.SetItemText(nIndex, 2, record_list->records[i].file_name);
		}
	
	} 

	delete record_list;
	record_list = NULL;
}

void CDlgStreamCloud::OnBnClickedBtnPlay()
{
	if (m_select_index == -1) {
		MessageBox("请选择待播放的文件!");
		return;
	}

	if (m_play_handle != 0) {
		VzClient_CloudStopRealPlay(m_play_handle);
		m_play_handle = 0;
	}

	CString file_name = m_lstRecordFile.GetItemText(m_select_index, 2);

	int out_code = 0;
	char out_buffer[256] = {0};
	int out_len = 256;
	int ret = VzClient_StreamCloudVod(m_hLPRClient, file_name.GetBuffer(0), &out_code, out_buffer, out_len);
	if (strcmp(out_buffer, "") != 0) {
		m_play_handle = VzClient_StreamOpenHLS(out_buffer, 0, m_play_wnd.GetSafeHwnd());
	}
}


void CDlgStreamCloud::OnLvnItemchangedListPlate(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMLISTVIEW pNMLV = reinterpret_cast<LPNMLISTVIEW>(pNMHDR);
	// TODO: Add your control notification handler code here
	if((pNMLV->uChanged & LVIF_STATE) && (pNMLV->uNewState & LVIS_SELECTED)) {
		m_select_index =  pNMLV->iItem;
	}

	*pResult = 0;
}

void CDlgStreamCloud::OnClose()
{
	if (m_play_handle != 0) {
		VzClient_CloudStopRealPlay(m_play_handle);
		m_play_handle = 0;
	}

	CDialog::OnClose();
}
