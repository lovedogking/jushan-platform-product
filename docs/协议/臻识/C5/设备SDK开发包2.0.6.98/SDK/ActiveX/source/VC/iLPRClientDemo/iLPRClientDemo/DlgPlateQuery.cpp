// DlgPlateQuery.cpp : implementation file
//

#include "stdafx.h"
#include "DlgPlateQuery.h"

const int ONE_PAGE_COUNT = 20; // 一页记录的条数
// CDlgPlateQuery dialog

IMPLEMENT_DYNAMIC(CDlgPlateQuery, CDialog)

CDlgPlateQuery::CDlgPlateQuery(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgPlateQuery::IDD, pParent)
	, m_dtStart(COleDateTime::GetCurrentTime())
	, m_dtStartTime(COleDateTime::GetCurrentTime())
	, m_dtEnd(COleDateTime::GetCurrentTime())
	, m_dtEndTime(COleDateTime::GetCurrentTime())
	, m_strPlate(_T(""))
	, m_strPageMsg(_T(""))
{
	m_nLprHandle = 0;
}

CDlgPlateQuery::~CDlgPlateQuery()
{
}

void CDlgPlateQuery::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_LIST_PLATE, m_lstPlate);
	DDX_DateTimeCtrl(pDX, IDC_DT_START, m_dtStart);
	DDX_DateTimeCtrl(pDX, IDC_DT_START_TIME, m_dtStartTime);
	DDX_DateTimeCtrl(pDX, IDC_DT_END, m_dtEnd);
	DDX_DateTimeCtrl(pDX, IDC_DT_END_TIME, m_dtEndTime);
	DDX_Text(pDX, IDC_EDIT_PLATE, m_strPlate);
	DDV_MaxChars(pDX, m_strPlate, 16);
	DDX_Control(pDX, IDC_VZLPRCLIENTCTRLCTRL1, m_lprClientCtrl);
	DDX_Text(pDX, IDC_LBL_MSG, m_strPageMsg);
}


BEGIN_MESSAGE_MAP(CDlgPlateQuery, CDialog)
	ON_BN_CLICKED(IDC_BTN_UP, &CDlgPlateQuery::OnBnClickedBtnUp)
	ON_BN_CLICKED(IDC_BTN_DOWN, &CDlgPlateQuery::OnBnClickedBtnDown)
	ON_WM_CLOSE()
	ON_BN_CLICKED(IDC_BTN_QUERY, &CDlgPlateQuery::OnBnClickedBtnQuery)
END_MESSAGE_MAP()


// CDlgPlateQuery message handlers

void CDlgPlateQuery::OnBnClickedBtnUp()
{
	int nCurPage = m_nCurPage - 1;
	if( nCurPage >= 0 )
	{
		QueryByPage(nCurPage);

		m_nCurPage--;
		char szPageMsg[128] = {0};
		sprintf_s(szPageMsg, sizeof(szPageMsg), "共%d条记录, %d/%d", m_nTotalCount, m_nCurPage + 1, m_nPageCount );
		m_strPageMsg = szPageMsg;
		UpdateData(FALSE);
	}
	else
	{
		MessageBox("无上一页记录!", "提示", MB_OK );
	}
}

void CDlgPlateQuery::OnBnClickedBtnDown()
{
	int nCurPage = m_nCurPage + 1;
	if( nCurPage < m_nPageCount )
	{
		QueryByPage(nCurPage);

		m_nCurPage++;
		char szPageMsg[128] = {0};
		sprintf_s(szPageMsg, sizeof(szPageMsg), "共%d条记录, %d/%d", m_nTotalCount, m_nCurPage + 1, m_nPageCount );
		m_strPageMsg = szPageMsg;
		UpdateData(FALSE);
	}
	else
	{
		MessageBox("无下一页记录!", "提示", MB_OK );
	}
}

BOOL CDlgPlateQuery::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_nLprHandle = m_lprClientCtrl.VzLPRClientOpen(m_strDeivceIP.GetBuffer(0), m_nPort, m_strUserName.GetBuffer(0), m_strPwd.GetBuffer(0));

	m_lstPlate.InsertColumn( 0, "记录ID",   LVCFMT_LEFT,   55  );
	m_lstPlate.InsertColumn( 1, "车牌号",   LVCFMT_LEFT,   120  );
	m_lstPlate.InsertColumn( 2, "记录时间",   LVCFMT_LEFT, 140  );
	m_lstPlate.InsertColumn( 3, "识别类型",   LVCFMT_LEFT, 90  );
	m_lstPlate.InsertColumn( 4, "车牌颜色",   LVCFMT_LEFT, 90  );

	m_lstPlate.SetExtendedStyle(m_lstPlate.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);

	m_dtStartTime.SetTime(0,0,0);
	UpdateData(FALSE);

	return TRUE;
}

void CDlgPlateQuery::SetDeviceIP( CString strIP, int nPort, CString strUserName, CString strPwd )
{
	m_strDeivceIP = strIP;
	m_nPort = nPort;
	m_strUserName = strUserName;
	m_strPwd = strPwd;
}


void CDlgPlateQuery::OnClose()
{
	// TODO: Add your message handler code here and/or call default
	if ( m_nLprHandle > 0 )
	{
		m_lprClientCtrl.VzLPRClientClose( m_nLprHandle );
		m_nLprHandle = 0;
	}

	CDialog::OnClose();
}

// 查询车牌记录的功能
void CDlgPlateQuery::OnBnClickedBtnQuery()
{
	m_lstPlate.DeleteAllItems( );

	m_nTotalCount	= 0;
	m_nCurPage		= 0; 
	m_nPageCount	= 0;

	m_strStartTime	= "";
	m_strEndTime	= "";

	if ( m_nLprHandle == 0 )
	{
		return;
	}

	UpdateData(TRUE);

	char szStatTime[128] = {0};
	sprintf_s( szStatTime, sizeof(szStatTime), "%d-%02d-%02d %02d:%02d:%02d", m_dtStart.GetYear(), m_dtStart.GetMonth(), m_dtStart.GetDay(),
		m_dtStartTime.GetHour(), m_dtStartTime.GetMinute(), m_dtStartTime.GetSecond() );

	char szEndTime[128]  = {0};
	sprintf_s(szEndTime, sizeof(szEndTime), "%d-%02d-%02d %02d:%02d:%02d", m_dtEnd.GetYear(), m_dtEnd.GetMonth(), m_dtEnd.GetDay(),
		m_dtEndTime.GetHour(), m_dtEndTime.GetMinute(), m_dtEndTime.GetSecond() );

	int count = m_lprClientCtrl.VzLPRQueryCountByTimeAndPlate(m_nLprHandle, szStatTime, szEndTime, m_strPlate.GetBuffer(0));

	char szPageMsg[128] = {0};

	if( count > 0 )
	{
		int pageSize = count / ONE_PAGE_COUNT;
		int nPageCount = ( count % ONE_PAGE_COUNT == 0 ) ? pageSize  : (pageSize + 1); 

		sprintf_s(szPageMsg, sizeof(szPageMsg), "共%d条记录, %d/%d", count, 1, nPageCount );
		m_nTotalCount	= count;
		m_nPageCount	= nPageCount;

		m_strStartTime	= szStatTime;
		m_strEndTime	= szEndTime;

		QueryByPage( 0 );
	}
	else
	{
		strcpy_s(szPageMsg, sizeof(szPageMsg), "共0条记录" );
	}

	m_strPageMsg = szPageMsg;
	UpdateData(FALSE);
}
BEGIN_EVENTSINK_MAP(CDlgPlateQuery, CDialog)
	ON_EVENT(CDlgPlateQuery, IDC_VZLPRCLIENTCTRLCTRL1, 12, CDlgPlateQuery::OnLPRQueryPlateInfoOutVzlprclientctrlctrl1, VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I4 VTS_I2 VTS_I2 VTS_BSTR VTS_BSTR VTS_I2 VTS_I4)
	ON_EVENT(CDlgPlateQuery, IDC_VZLPRCLIENTCTRLCTRL1, 19, CDlgPlateQuery::OnLPRQueryPlateInfoOutExVzlprclientctrlctrl1, VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I4 VTS_I2 VTS_I2 VTS_BSTR VTS_BSTR VTS_I2 VTS_I4 VTS_BSTR)
END_EVENTSINK_MAP()


void CDlgPlateQuery::OnLPRQueryPlateInfoOutVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
																short resultType, long plateID)
{
	
}

void CDlgPlateQuery::QueryByPage(int nPageIndex)
{
	m_lstPlate.DeleteAllItems( );
	m_lprClientCtrl.VzLPRQueryPageRecordByTimeAndPlate(m_nLprHandle, m_strStartTime.GetBuffer(0), m_strEndTime.GetBuffer(0), m_strPlate.GetBuffer(0), nPageIndex*ONE_PAGE_COUNT, (nPageIndex+1)*ONE_PAGE_COUNT );
}
void CDlgPlateQuery::OnLPRQueryPlateInfoOutExVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
																  short resultType, long plateID, LPCTSTR plateDateTime)
{
	CString strPlateID;
	strPlateID.Format("%d", plateID);

	int nIndex = m_lstPlate.InsertItem(0, strPlateID);
	m_lstPlate.SetItemData(nIndex, plateID);

	m_lstPlate.SetItemText(nIndex, 1, license);

	m_lstPlate.SetItemText(nIndex, 2, plateDateTime);

	if( resultType == 1 )
	{
		m_lstPlate.SetItemText(nIndex, 3, "稳定结果");
	}
	else if( resultType == 2 )
	{
		m_lstPlate.SetItemText(nIndex, 3, "手动触发");
	}
	else if( resultType == 3 )
	{
		m_lstPlate.SetItemText(nIndex, 3, "地感触发");
	}
	else if ( resultType == 4 )
	{
		m_lstPlate.SetItemText(nIndex, 3, "虚拟线圈");
	}
	else if ( resultType == 5 )
	{
		m_lstPlate.SetItemText(nIndex, 3, "多重触发");
	}

	if( colorIndex == 0 )
	{
		m_lstPlate.SetItemText(nIndex, 4, "未知");
	}
	else if( colorIndex == 1 )
	{
		m_lstPlate.SetItemText(nIndex, 4, "蓝色");
	}
	else if( colorIndex == 2 )
	{
		m_lstPlate.SetItemText(nIndex, 4, "黄色");
	}
	else if( colorIndex == 3 )
	{
		m_lstPlate.SetItemText(nIndex, 4, "白色");
	}
	else if( colorIndex == 4 )
	{
		m_lstPlate.SetItemText(nIndex, 4, "黑色");
	}
	else if( colorIndex == 5 )
	{
		m_lstPlate.SetItemText(nIndex, 4, "绿色");
	}
}
