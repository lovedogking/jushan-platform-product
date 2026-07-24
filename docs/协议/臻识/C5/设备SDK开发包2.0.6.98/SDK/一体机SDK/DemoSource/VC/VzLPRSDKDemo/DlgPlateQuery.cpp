// DlgPlateQuery.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgPlateQuery.h"

const int ONE_PAGE_COUNT = 20; // 一页记录的条数

int __stdcall gOnQueryPlateInfo(VzLPRClientHandle handle, void *pUserData,
							   const TH_PlateResult *pResult, unsigned uNumPlates,
							   VZ_LPRC_RESULT_TYPE eResultType,
							   const VZ_LPRC_IMAGE_INFO *pImgFull,
							   const VZ_LPRC_IMAGE_INFO *pImgPlateClip)
{
	if ( handle != NULL && pUserData != NULL && pResult != NULL )
	{
		TH_PlateResult *pData = (TH_PlateResult *)malloc( sizeof(TH_PlateResult) );
		memcpy(pData, pResult, sizeof(TH_PlateResult));

		CDlgPlateQuery *pDlg = (CDlgPlateQuery*)pUserData;
		pDlg->RecvPlateResult( pData, eResultType );
	}

	return 0;
}

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
	, m_bOffline(FALSE)
{
	m_hLPRClient = NULL;

	m_nTotalCount	= 0;
	m_nCurPage		= 0;
	m_nPageCount	= 0;

	m_bQueryOffline	= FALSE;

    m_uSizeBufJpg = 1024 * 1024 * 3;
    m_pBufJpg = new unsigned char [m_uSizeBufJpg];

    m_uSizeBufRGB = 2592 * 1944 * 3;
    m_pBufRGB = new unsigned char [m_uSizeBufRGB];
}

CDlgPlateQuery::~CDlgPlateQuery()
{
    delete []m_pBufJpg;
    delete []m_pBufRGB;
}

void CDlgPlateQuery::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
    DDX_Control(pDX, IDC_STATIC_SHOW_QUERY, m_winShow.m_struInterV);
	DDX_DateTimeCtrl(pDX, IDC_DT_START, m_dtStart);
	DDX_DateTimeCtrl(pDX, IDC_DT_START_TIME, m_dtStartTime);
	DDX_DateTimeCtrl(pDX, IDC_DT_END, m_dtEnd);
	DDX_DateTimeCtrl(pDX, IDC_DT_END_TIME, m_dtEndTime);
	DDX_Text(pDX, IDC_EDIT_PLATE, m_strPlate);
	DDX_Control(pDX, IDC_LIST_PLATE, m_lstPlate);
	DDX_Text(pDX, IDC_LBL_MSG, m_strPageMsg);
	DDX_Check(pDX, IDC_CHK_OFFLINE, m_bOffline);
}


BEGIN_MESSAGE_MAP(CDlgPlateQuery, CDialog)
	ON_BN_CLICKED(IDC_BTN_QUERY, &CDlgPlateQuery::OnBnClickedBtnQuery)
	ON_MESSAGE(WM_PLATE_RESULT_MESSAGE, &CDlgPlateQuery::OnPlateMessage)
	ON_BN_CLICKED(IDC_BTN_UP, &CDlgPlateQuery::OnBnClickedBtnUp)
	ON_BN_CLICKED(IDC_BTN_DOWN, &CDlgPlateQuery::OnBnClickedBtnDown)
	ON_BN_CLICKED(IDC_CHK_OFFLINE, &CDlgPlateQuery::OnBnClickedChkOffline)
    ON_NOTIFY(LVN_ITEMCHANGED, IDC_LIST_PLATE, &CDlgPlateQuery::OnLvnItemchangedListPlate)
END_MESSAGE_MAP()


// CDlgPlateQuery message handlers

void CALLBACK CDlgPlateQuery::iOnIVPaint(int nID, bool bActive, bool bInUse, void *pUserData)
{
	CDlgPlateQuery *pInstance = (CDlgPlateQuery *)pUserData;
	pInstance->m_winShow.ShowFrame();
}


BOOL CDlgPlateQuery::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here
	m_lstPlate.InsertColumn( 0, "记录ID",   LVCFMT_LEFT,   55  );
	m_lstPlate.InsertColumn( 1, "车牌号",   LVCFMT_LEFT,   120  );
	m_lstPlate.InsertColumn( 2, "记录时间",   LVCFMT_LEFT, 140  );
	m_lstPlate.InsertColumn( 3, "识别类型",   LVCFMT_LEFT, 80  );
	m_lstPlate.InsertColumn( 4, "车牌颜色",   LVCFMT_LEFT, 50  );

	m_lstPlate.SetExtendedStyle(m_lstPlate.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);

	if ( m_hLPRClient != NULL )
	{
		VzLPRClient_SetQueryPlateCallBack(m_hLPRClient, gOnQueryPlateInfo, this );
	}

    m_winShow.m_struInterV.SetCallBackOnPaint(CDlgPlateQuery::iOnIVPaint, this);

	m_dtStartTime.SetTime(0,0,0);
	UpdateData(FALSE);

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}

void CDlgPlateQuery::OnBnClickedBtnQuery()
{
	m_lstPlate.DeleteAllItems( );

	m_nTotalCount	= 0;
	m_nCurPage		= 0; 
	m_nPageCount	= 0;

	m_strStartTime	= "";
	m_strEndTime	= "";

	if ( m_hLPRClient == NULL )
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

	int count = 0;
	
	if( m_bOffline )
	{
		count = VzLPRClient_QueryOfflineCountByTime(m_hLPRClient, szStatTime, szEndTime );
	}
	else
	{
		count = VzLPRClient_QueryCountByTimeAndPlate(m_hLPRClient, szStatTime, szEndTime, m_strPlate.GetBuffer(0));
	}

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

		m_bQueryOffline = m_bOffline;

		QueryByPage( 0 );
	}
	else
	{
		strcpy_s(szPageMsg, sizeof(szPageMsg), "共0条记录" );
	}

	m_strPageMsg = szPageMsg;
	UpdateData(FALSE);
}

// 根据页数来进行查询
void CDlgPlateQuery::QueryByPage(int nPageIndex)
{
	m_lstPlate.DeleteAllItems( );

	if( m_bQueryOffline )
	{
		VzLPRClient_QueryPageOfflineRecordByTime(m_hLPRClient, m_strStartTime.GetBuffer(0), m_strEndTime.GetBuffer(0), nPageIndex*ONE_PAGE_COUNT, (nPageIndex+1)*ONE_PAGE_COUNT );
	}
	else
	{
		VzLPRClient_QueryPageRecordByTimeAndPlate(m_hLPRClient, m_strStartTime.GetBuffer(0), m_strEndTime.GetBuffer(0), m_strPlate.GetBuffer(0), nPageIndex*ONE_PAGE_COUNT, (nPageIndex+1)*ONE_PAGE_COUNT );
	}
}

void CDlgPlateQuery::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgPlateQuery::RecvPlateResult( TH_PlateResult *result, VZ_LPRC_RESULT_TYPE eResultType)
{
	PostMessage(WM_PLATE_RESULT_MESSAGE, (WPARAM)result, (LPARAM)eResultType);
}

bool WriteTxtFile(const char *filepath, LPCSTR strText)
{
	int num = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "a+");

	if( fp == NULL )
	{
		return false;
	}

	num = fwrite( strText, sizeof(char), strlen(strText), fp );
	fclose( fp );

	return num > 0;
}

LRESULT CDlgPlateQuery::OnPlateMessage(WPARAM wParam, LPARAM lParam)
{
	TH_PlateResult *pData		= (TH_PlateResult *)wParam;
	VZ_LPRC_RESULT_TYPE eType	= (VZ_LPRC_RESULT_TYPE)lParam;
	
	if( pData != NULL )
	{
		// char szOutput[128] = {0};

		CString strPlateID;
		strPlateID.Format("%d", pData->uId);

		int nIndex = m_lstPlate.InsertItem(0, strPlateID);
		m_lstPlate.SetItemData(nIndex, pData->uId);

		m_lstPlate.SetItemText(nIndex, 1, pData->license);

		char szTime[64] = {0};
		sprintf_s( szTime, sizeof(szTime), "%d-%02d-%02d %02d:%02d:%02d",  pData->struBDTime.bdt_year, pData->struBDTime.bdt_mon, pData->struBDTime.bdt_mday,  
			pData->struBDTime.bdt_hour, pData->struBDTime.bdt_min,pData->struBDTime.bdt_sec);  

		m_lstPlate.SetItemText(nIndex, 2, szTime);

		// sprintf( szOutput, "%d %s %s", pData->uId, pData->license, szTime );

		if( eType == VZ_LPRC_RESULT_STABLE )
		{
			m_lstPlate.SetItemText(nIndex, 3, "稳定结果");
		}
		else if( eType == VZ_LPRC_RESULT_FORCE_TRIGGER )
		{
			m_lstPlate.SetItemText(nIndex, 3, "手动触发");
		}
		else if( eType == VZ_LPRC_RESULT_IO_TRIGGER )
		{
			m_lstPlate.SetItemText(nIndex, 3, "地感触发");
		}
		else if ( eType == VZ_LPRC_RESULT_VLOOP_TRIGGER )
		{
			m_lstPlate.SetItemText(nIndex, 3, "虚拟线圈");
		}
		else if ( eType == VZ_LPRC_RESULT_MULTI_TRIGGER )
		{
			m_lstPlate.SetItemText(nIndex, 3, "多重触发");
		}

		if( pData->nColor == LC_UNKNOWN )
		{
			m_lstPlate.SetItemText(nIndex, 4, "未知");
		}
		else if( pData->nColor == LC_BLUE )
		{
			m_lstPlate.SetItemText(nIndex, 4, "蓝色");
			//strcat(szOutput, " 蓝色");
		}
		else if( pData->nColor == LC_YELLOW )
		{
			m_lstPlate.SetItemText(nIndex, 4, "黄色");
			//strcat(szOutput, " 黄色");
		}
		else if( pData->nColor == LC_WHITE )
		{
			m_lstPlate.SetItemText(nIndex, 4, "白色");
			//strcat(szOutput, " 白色");
		}
		else if( pData->nColor == LC_BLACK )
		{
			m_lstPlate.SetItemText(nIndex, 4, "黑色");
			//strcat(szOutput, " 黑色");
		}
		else if( pData->nColor == LC_GREEN )
		{
			m_lstPlate.SetItemText(nIndex, 4, "绿色");
			//strcat(szOutput, " 绿色");
		}

		//strcat(szOutput, "\n");
		////WriteTxtFile("D:\\test.txt", szOutput);

		free(pData);
	}

	return 0;
}
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

void CDlgPlateQuery::OnBnClickedChkOffline()
{
	// TODO: Add your control notification handler code here
	UpdateData(TRUE);
	CWnd *pEditWnd = GetDlgItem(IDC_EDIT_PLATE);
	if( pEditWnd == NULL )
	{
		return;
	}

	if( m_bOffline )
	{
		pEditWnd->EnableWindow(FALSE);
	}
	else
	{
		pEditWnd->EnableWindow(TRUE);
	}
}

void CDlgPlateQuery::OnLvnItemchangedListPlate(NMHDR *pNMHDR, LRESULT *pResult)
{
    LPNMLISTVIEW pNMLV = reinterpret_cast<LPNMLISTVIEW>(pNMHDR);
    // TODO: Add your control notification handler code here
    if((pNMLV->uChanged & LVIF_STATE) && (pNMLV->uNewState & LVIS_SELECTED))
    {
        int nItem =  pNMLV->iItem;
        const int MaxLenId = 32;
        char strId[MaxLenId];
        m_lstPlate.GetItemText(nItem, 0, strId, MaxLenId);

        int nSizeJpg = m_uSizeBufJpg;
        int nRT = VzLPRClient_LoadImageById(m_hLPRClient, 
            atoi(strId), m_pBufJpg, &nSizeJpg);
        if(nRT == VZ_API_FAILED)
        {
            return;
        }
        VZ_LPRC_IMAGE_INFO struImage = {0};
        nRT = VzLPRClient_JpegDecodeToImage(m_pBufJpg, nSizeJpg,
            &struImage, m_pBufRGB, m_uSizeBufRGB);
        if(nRT == VZ_API_FAILED)
            return;

        m_winShow.SetFrame(struImage.pBuffer, struImage.uWidth, struImage.uHeight, struImage.uPitch);
    }
    *pResult = 0;
}
