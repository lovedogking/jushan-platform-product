// DlgWlist.cpp : implementation file
//

#include "stdafx.h"
#include "VZLPRClientDemo.h"
#include "DlgWlist.h"
#include "DlgPlateInfo.h"
#include "VzFile.h"

const int ONE_PAGE_COUNT = 20;

void OnNewImportItem(void *item_str, std::size_t size, void *pdata) 
{
	CDlgWlist *pDlg = (CDlgWlist *)pdata;
	if (pDlg != NULL)
	{
		pDlg->InsertWlistRecord(item_str, (size_t)size);
	}
}
void OnEndOfLine(int c, void *pdata) 
{
	CDlgWlist *pDlg = (CDlgWlist *)pdata;
	if (pDlg != NULL)
	{
		pDlg->EndInsertRecord( );
	}
}

// CDlgWlist dialog
IMPLEMENT_DYNAMIC(CDlgWlist, CDialog)

CDlgWlist::CDlgWlist(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgWlist::IDD, pParent)
{
	m_nLprHandle = 0;

	m_nTotalCount	= 0;
	m_nCurPage		= 0;
	m_nPageCount	= 0;
	m_nParseIndex	= 0;
}

CDlgWlist::~CDlgWlist()
{
}

void CDlgWlist::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_LIST_PLATE, m_lstPlate);
	DDX_Control(pDX, IDC_EDIT_PLATE, m_editPlate);
	DDX_Control(pDX, IDC_VZLPRCLIENTCTRLCTRL1, m_lprClientCtrl);
	DDX_Control(pDX, IDC_LBL_PAGE_INFO, m_lblPageInfo);
	DDX_Control(pDX, IDC_LBL_COUNT_INFO, m_lblCountInfo);
}


BEGIN_MESSAGE_MAP(CDlgWlist, CDialog)
	ON_WM_CLOSE()
	ON_BN_CLICKED(IDC_BTN_ADD, &CDlgWlist::OnBnClickedBtnAdd)
	ON_BN_CLICKED(IDC_BTN_EDIT, &CDlgWlist::OnBnClickedBtnEdit)
	ON_BN_CLICKED(IDC_BTN_DELE, &CDlgWlist::OnBnClickedBtnDele)
	ON_BN_CLICKED(IDC_BTN_QUERY, &CDlgWlist::OnBnClickedBtnQuery)
	ON_BN_CLICKED(IDC_BIN_CLEAR, &CDlgWlist::OnBnClickedBinClear)
	ON_NOTIFY(LVN_ITEMCHANGED, IDC_LIST_PLATE, &CDlgWlist::OnLvnItemchangedListPlate)
	ON_BN_CLICKED(IDC_BIN_BATCH_IMPORT, &CDlgWlist::OnBnClickedBinBatchImport)
	ON_BN_CLICKED(IDC_BTN_FIRST_PAGE, &CDlgWlist::OnBnClickedBtnFirstPage)
	ON_BN_CLICKED(IDC_BTN_PREV_PAGE, &CDlgWlist::OnBnClickedBtnPrevPage)
	ON_BN_CLICKED(IDC_BTN_NEXT_PAGE, &CDlgWlist::OnBnClickedBtnNextPage)
	ON_BN_CLICKED(IDC_BTN_LAST_PAGE, &CDlgWlist::OnBnClickedBtnLastPage)
END_MESSAGE_MAP()


// CDlgWlist message handlers

BOOL CDlgWlist::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_lstPlate.InsertColumn( 0, "车牌ID",   LVCFMT_LEFT,   60  );
	m_lstPlate.InsertColumn( 1, "车牌号",   LVCFMT_LEFT,   100  );
	m_lstPlate.InsertColumn( 2, "是否启用",   LVCFMT_LEFT, 80  );
	m_lstPlate.InsertColumn( 3, "过期时间",   LVCFMT_LEFT, 140  );
	m_lstPlate.InsertColumn( 4, "是否报警",   LVCFMT_LEFT, 80  );
	m_lstPlate.InsertColumn( 5, "车辆备注",   LVCFMT_LEFT, 100  );

	m_lstPlate.SetExtendedStyle(m_lstPlate.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);

	m_nLprHandle = m_lprClientCtrl.VzLPRClientOpen(m_strDeivceIP.GetBuffer(0), m_nPort, m_strUserName.GetBuffer(0), m_strPwd.GetBuffer(0));

	QueryWlist( );

	return TRUE;
}

void CDlgWlist::OnClose()
{
	// TODO: Add your message handler code here and/or call default
	if ( m_nLprHandle > 0 )
	{
		m_lprClientCtrl.VzLPRClientClose( m_nLprHandle );
		m_nLprHandle = 0;
	}

	CDialog::OnClose();
}
BEGIN_EVENTSINK_MAP(CDlgWlist, CDialog)
	ON_EVENT(CDlgWlist, IDC_VZLPRCLIENTCTRLCTRL1, 11, CDlgWlist::OnLPRWlistInfoOutVzlprclientctrlctrl1, VTS_I4 VTS_BSTR VTS_BOOL VTS_BSTR VTS_BOOL VTS_BOOL VTS_BSTR VTS_I4 VTS_BSTR VTS_BSTR)
	ON_EVENT(CDlgWlist, IDC_VZLPRCLIENTCTRLCTRL1, 17, CDlgWlist::OnLPRWlistInfoOutExVzlprclientctrlctrl1, VTS_I4 VTS_BSTR VTS_BOOL VTS_BOOL VTS_BOOL VTS_BSTR VTS_BSTR VTS_BOOL VTS_BOOL VTS_BSTR VTS_BSTR VTS_I4 VTS_BSTR VTS_BSTR)
END_EVENTSINK_MAP()

// 查询车牌号的返回
void CDlgWlist::OnLPRWlistInfoOutVzlprclientctrlctrl1(long lVehicleID, LPCTSTR strPlateID, BOOL bEnable, LPCTSTR strOverdule, BOOL bUsingTimeSeg, BOOL bAlarm, LPCTSTR strReserved, long lCustomerID, LPCTSTR strCustomerName, LPCTSTR strCustomerCode)
{
	
}

void CDlgWlist::OnBnClickedBtnAdd()
{
	CDlgPlateInfo dlg;
	if( IDOK == dlg.DoModal() )
	{
		CString strPlate;
		BOOL bEnable;
		COleDateTime dt;
		BOOL bAlarm;
		CString strComment;

		
		dlg.GetPlateInfo(strPlate, bEnable, dt, bAlarm, strComment);

		CString strOverDate;
		strOverDate.Format( "%d-%02d-%02d %02d:%02d:%02d", dt.GetYear(), dt.GetMonth(), dt.GetDay(), dt.GetHour(), dt.GetMonth(), dt.GetSecond() );

		short ret= m_lprClientCtrl.VzLPRClientAddWlistEx( strPlate, 0, bEnable, FALSE, TRUE, "", strOverDate.GetBuffer(0), false, bAlarm, strPlate,strComment, m_nLprHandle );
		if ( ret == 0 )
		{
			QueryWlist( );
		}
	}
}

void CDlgWlist::OnBnClickedBtnEdit()
{
	int nCurSel = GetSeleIndex( );
	if ( nCurSel >= 0 )
	{
		CString strPlate	= m_lstPlate.GetItemText(nCurSel, 1);

		CString strEnable	= m_lstPlate.GetItemText(nCurSel, 2);
		BOOL bEnable	= (strEnable == "是") ? TRUE : FALSE;

		CString strOverTime = m_lstPlate.GetItemText(nCurSel, 3);
		COleDateTime dt;
		bool bParse = dt.ParseDateTime( strOverTime );
		if( !bParse )
		{
			dt = COleDateTime::GetCurrentTime();
		}

		CString strAlarm = m_lstPlate.GetItemText(nCurSel, 4);
		BOOL bAlarm		 = (strAlarm == "是") ? TRUE : FALSE;

		CString strComment = m_lstPlate.GetItemText(nCurSel, 5);

		CDlgPlateInfo dlg;
		dlg.SetPlateInfo(strPlate, bEnable, dt, bAlarm, strComment);

		if( IDOK == dlg.DoModal() )
		{
			dlg.GetPlateInfo(strPlate, bEnable, dt, bAlarm, strComment);

			int nUserID = m_lstPlate.GetItemData( nCurSel );

			CString strOverDate;
			strOverDate.Format( "%d-%02d-%02d %02d:%02d:%02d", dt.GetYear(), dt.GetMonth(), dt.GetDay(), dt.GetHour(), dt.GetMonth(), dt.GetSecond() );

			short ret= m_lprClientCtrl.VzLPRClientUpdateWlistEx( strPlate, 0, bEnable, FALSE, TRUE, "", strOverDate.GetBuffer(0), false, bAlarm, "", strComment, nUserID, m_nLprHandle );
			if ( ret == 0 )
			{
				QueryWlist( );
			}
		}
	}
	else
	{
		MessageBox("请选择一条记录", "提示", MB_OK );
	}
}

void CDlgWlist::OnBnClickedBtnDele()
{
	int nCurSel = GetSeleIndex( );
	if ( nCurSel >= 0 )
	{
		CString strPlate	= m_lstPlate.GetItemText(nCurSel, 1);
		short ret= m_lprClientCtrl.VzLPRClientDeleteWlist( strPlate, m_nLprHandle );
		if ( ret == 0 )
		{
			QueryWlist( );
		}
	}
	else
	{
		MessageBox("请选择一条记录", "提示", MB_OK );
	}
}

void CDlgWlist::OnBnClickedBtnQuery()
{
	CString strPlate;
	m_editPlate.GetWindowText(strPlate);
	
	if ( m_nLprHandle > 0 )
	{
		m_lstPlate.DeleteAllItems();
		QueryWlist( );
	}
}

void CDlgWlist::SetDeviceIP( CString strIP, int nPort, CString strUserName, CString strPwd )
{
	m_strDeivceIP = strIP;
	m_nPort = nPort;
	m_strUserName = strUserName;
	m_strPwd = strPwd;
}

// 加载车牌列表
void CDlgWlist::LoadPlateList( CString strPlateKey )
{
	m_lstPlate.DeleteAllItems( );

	if ( m_nLprHandle > 0 )
	{
		m_lprClientCtrl.VzLPRClientQueryWlistByPlate(strPlateKey, m_nLprHandle);
	}
}

int CDlgWlist::GetSeleIndex( )
{
	int nCurSel = -1;

	for(int i=0; i< m_lstPlate.GetItemCount(); i++)
	{
		if( m_lstPlate.GetItemState(i, LVIS_SELECTED) == LVIS_SELECTED )
		{
			nCurSel = i;
			break;
		}
	}

	return nCurSel;
}
void CDlgWlist::OnLPRWlistInfoOutExVzlprclientctrlctrl1(long lVehicleID, LPCTSTR strPlateID, BOOL bEnable, BOOL bEnableTM, BOOL bEnableOverdule, LPCTSTR strTMEnable, LPCTSTR strOverdule, BOOL bUsingTimeSeg, BOOL bAlarm, LPCTSTR strPlateCode, 
														LPCTSTR strPlateComment, long lCustomerID, LPCTSTR strCustomerName, LPCTSTR strCustomerCode)
{
	CString strVehicleID;
	strVehicleID.Format("%d", lVehicleID);

	CString strEnable = bEnable ? "是" : "否";
	CString strAlarm  = bAlarm ?  "是" : "否";

	int nCount = m_lstPlate.GetItemCount();

	int nIndex = m_lstPlate.InsertItem(nCount, strVehicleID);
	m_lstPlate.SetItemData(nIndex, lVehicleID);

	m_lstPlate.SetItemText(nIndex, 1, strPlateID);
	m_lstPlate.SetItemText(nIndex, 2, strEnable);
	m_lstPlate.SetItemText(nIndex, 3, strOverdule);
	m_lstPlate.SetItemText(nIndex, 4, strAlarm);
	m_lstPlate.SetItemText(nIndex, 5, strPlateComment);
}


BOOL CDlgWlist::PreTranslateMessage(MSG* pMsg)
{
	// TODO: Add your specialized code here and/or call the base class
	if (pMsg->message == WM_KEYDOWN)  
	{  
		switch(pMsg->wParam)  
		{  
		case VK_ESCAPE: //Esc按键事件  
			return true;  

		case VK_RETURN: //Enter按键事件  
			return true;  

		default:  
			break;  
		}  
	}  

	return CDialog::PreTranslateMessage(pMsg);
}

void CDlgWlist::OnBnClickedBinClear()
{
	// TODO: Add your control notification handler code here
	m_lstPlate.DeleteAllItems( );
	if (m_nLprHandle > 0)
	{
		int ret = m_lprClientCtrl.VzLPRWhiteListClear(m_nLprHandle);
		if (0 == ret )
		{
			MessageBox(_T("白名单已清空"), _T("提示"), MB_OK );

			m_lstPlate.DeleteAllItems();

			m_lblPageInfo.SetWindowText("第1/1页");
			m_lblCountInfo.SetWindowText("共0条记录");
		}
	}
}

void CDlgWlist::OnLvnItemchangedListPlate(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMLISTVIEW pNMLV = reinterpret_cast<LPNMLISTVIEW>(pNMHDR);
	// TODO: Add your control notification handler code here
	*pResult = 0;
}

// 批量导入白名单的功能。
void CDlgWlist::OnBnClickedBinBatchImport()
{
	// 清空保存在缓存
	int ret = m_lprClientCtrl.VzLPRClientRemoveWlistFromArray(m_nLprHandle);

	string sCvsPath = CVzFile::BrowsPath("", "CSV文件 *.csv\0*.csv\0\0", this->GetSafeHwnd());
	memset(&m_vehicle, 0, sizeof(LPR_WLIST_VEHICLE));

	// 获取记录条数
	int nCount = CVzFile::GetLineCount(sCvsPath.c_str());
	if (nCount > 1)
	{
		FILE *fp = NULL;
		fp = fopen(sCvsPath.c_str(), "r");

		if (fp == NULL)
		{
			return;
		}

		csv_parser parser;
		csv_init(&parser, 0);

		const int max_len = 1024;
		char buffer[max_len] = { 0 };

		// 去掉第一行标题头
		fgets(buffer, max_len, fp);

		while (true)
		{
			memset(buffer, 0, max_len);
			fgets(buffer, max_len, fp);

			// 读取数据失败
			int nCount = strlen(buffer);
			if (nCount == 0)
			{
				break;
			}

			// 解析失败
			size_t parse_size = csv_parse(&parser, buffer, nCount, OnNewImportItem, OnEndOfLine, this);
			if (parse_size != nCount)
			{
				break;
			}
		}

		csv_free(&parser);
		fclose(fp);

		// 执行批量导入的功能
		ret = m_lprClientCtrl.VzLPRClientBatchImportWlistFromArray(m_nLprHandle);
		if (0 == ret )
		{
			QueryWlist( );
			MessageBox(_T("批量导入成功"), _T("提示"), MB_OK );
		}
	}
	else
	{
		MessageBox(_T("读取到白名单数据失败，请检查导入的文件！"), _T("提示"), MB_OK );
	}

	
}

// 首页
void CDlgWlist::OnBnClickedBtnFirstPage()
{
	m_lstPlate.DeleteAllItems();
	QueryByPage(0);

	m_nCurPage = 0;

	char szPageMsg[64] = { 0 };
	sprintf_s(szPageMsg, sizeof(szPageMsg), "第%d/%d页", m_nCurPage + 1, m_nPageCount );
	m_lblPageInfo.SetWindowText(szPageMsg);
}

// 上一页 
void CDlgWlist::OnBnClickedBtnPrevPage()
{
	int nCurPage = m_nCurPage - 1;
	if (nCurPage >= 0)
	{
		m_lstPlate.DeleteAllItems();
		QueryByPage(nCurPage);

		m_nCurPage--;

		char szPageMsg[64] = { 0 };
		sprintf_s(szPageMsg, sizeof(szPageMsg), "第%d/%d页", m_nCurPage + 1, m_nPageCount);
		m_lblPageInfo.SetWindowText(szPageMsg);
	}
	else
	{
		MessageBox("无上一页记录!");
	}
}

// 下一页
void CDlgWlist::OnBnClickedBtnNextPage()
{
	int nCurPage = m_nCurPage + 1;
	if (nCurPage < m_nPageCount)
	{
		m_lstPlate.DeleteAllItems();
		QueryByPage(nCurPage);

		m_nCurPage++;

		char szPageMsg[64] = { 0 };
		sprintf_s(szPageMsg, sizeof(szPageMsg), "第%d/%d页", m_nCurPage + 1, m_nPageCount );
		m_lblPageInfo.SetWindowText(szPageMsg);
	}
	else
	{
		MessageBox("无下一页记录!");
	}
}

// 尾页
void CDlgWlist::OnBnClickedBtnLastPage()
{
	m_lstPlate.DeleteAllItems();
	m_nCurPage = m_nPageCount - 1;
	QueryByPage(m_nCurPage);

	char szPageMsg[64] = { 0 };
	sprintf_s(szPageMsg, sizeof(szPageMsg), "第%d/%d页", m_nPageCount, m_nPageCount);
	m_lblPageInfo.SetWindowText(szPageMsg);
}

// 分页查询记录
void CDlgWlist::QueryByPage(int nPageIndex)
{
	int nStartIndex = nPageIndex * ONE_PAGE_COUNT;

	m_lprClientCtrl.VzLPRClientQueryLimitWlistByPlate(m_strPlate, nStartIndex, ONE_PAGE_COUNT, m_nLprHandle);
}

// 查询白名单
void CDlgWlist::QueryWlist( )
{
	m_nTotalCount = 0;
	m_nCurPage = 0;
	m_nPageCount = 0;

	m_lstPlate.DeleteAllItems( );

	m_editPlate.GetWindowText(m_strPlate);
	
	// 获取记录的条数
	int nRecordCount = m_lprClientCtrl.VzLPRClientQueryWlistCount(m_strPlate, m_nLprHandle);

	// 计算记录页数
	int pageSize = nRecordCount / ONE_PAGE_COUNT;
	int nPageCount = (nRecordCount % ONE_PAGE_COUNT == 0) ? pageSize : (pageSize + 1);

	char szPageMsg[64] = { 0 };
	sprintf_s(szPageMsg, sizeof(szPageMsg), "第%d/%d页", m_nPageCount + 1, nPageCount);

	m_lblPageInfo.SetWindowText(szPageMsg);

	m_nTotalCount = nRecordCount;
	m_nPageCount = nPageCount;

	char szCountMsg[64] = { 0 };
	sprintf_s(szCountMsg, sizeof(szCountMsg), "共%d条记录", nRecordCount);
	m_lblCountInfo.SetWindowText(szCountMsg);

	QueryByPage( 0 );
}

void CDlgWlist::InsertWlistRecord(void *item_str, size_t size)
{
	// 将数据转换成字符串
	const int max_field_len = 128;	// 一个字段最大的字符数
	char szValue[max_field_len] = { 0 };

	if (item_str != NULL && size > 0 && size < max_field_len)
	{
		memcpy(szValue, item_str, size);
	}

	if ( strlen(szValue) > 0 )
	{ 
		COleDateTime dt;
		string sPeriod;

		switch (m_nParseIndex)
		{
		case READ_ID:
			m_vehicle.uVehicleID = atoi(szValue);
			break;

		case READ_PLATE:
			strncpy_s(m_vehicle.strPlateID, sizeof(m_vehicle.strPlateID), szValue, sizeof(m_vehicle.strPlateID) - 1);
			break;

		case READ_ENABLE:
			m_vehicle.bEnable = (strcmp(szValue, "是") == 0) ? 1 : 0;
			break;

		case READ_CREATE_TIME:
			if (dt.ParseDateTime(szValue))
			{
				sprintf(m_vehicle.strTMEnable, "%d-%02d-%02d %02d:%02d:%02d ", dt.GetYear(), dt.GetMonth(), dt.GetDay(), dt.GetHour(), dt.GetMinute(), dt.GetSecond());
				m_vehicle.bEnableTMEnable = 1;
			}
			break;

		case READ_EXPIRES_TIME:
			if (dt.ParseDateTime(szValue))
			{
				sprintf(m_vehicle.strTMOverdule, "%d-%02d-%02d %02d:%02d:%02d ", dt.GetYear(), dt.GetMonth(), dt.GetDay(), dt.GetHour(), dt.GetMinute(), dt.GetSecond());
				m_vehicle.bEnableTMOverdule = 1;
			}
			break;

		case READ_ENABLE_TIME_SEG:
			m_vehicle.bUsingTimeSeg = (strcmp(szValue, "是") == 0) ? 1 : 0;
			break;

		case READ_TIME_SEG:
			break;

		case READ_ENABLE_BLACK_LIST:
			m_vehicle.bAlarm = (strcmp(szValue, "是") == 0) ? 1 : 0;
			break;

		case READ_CUSTOMER_CODE:
			strncpy_s(m_vehicle.strCode, sizeof(m_vehicle.strCode), szValue, sizeof(m_vehicle.strCode) - 1);
			break;

		case READ_CUSTOMER_ENCODE:
			strncpy_s(m_vehicle.strComment, sizeof(m_vehicle.strComment), szValue, sizeof(m_vehicle.strComment) - 1);
			break;

		default:
			break;
		}
	}

	m_nParseIndex++;

}

void CDlgWlist::EndInsertRecord()
{
	m_lprClientCtrl.VzLPRClientAddWlistToArray(m_vehicle.strPlateID, 0, m_vehicle.bEnable, m_vehicle.bEnableTMEnable, m_vehicle.bEnableTMOverdule, m_vehicle.strTMEnable, m_vehicle.strTMOverdule,FALSE, m_vehicle.bAlarm, m_vehicle.strCode, m_vehicle.strComment, m_nLprHandle );

	memset(&m_vehicle, 0, sizeof(LPR_WLIST_VEHICLE));
	m_nParseIndex = 0;
}