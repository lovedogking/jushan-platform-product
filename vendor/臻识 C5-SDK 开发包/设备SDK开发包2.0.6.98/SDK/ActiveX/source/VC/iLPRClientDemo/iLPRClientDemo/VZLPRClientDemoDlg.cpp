// VZLPRClientDemoDlg.cpp : implementation file
//

#include "stdafx.h"
#include "VZLPRClientDemo.h"
#include "VZLPRClientDemoDlg.h"
#include "VzFile.h"
#include "DlgPlateQuery.h"
#include <algorithm>
#include "DlgLedCtrl.h"
#include "ExtDlg.h"
#include "DlgUserPwd.h"
#include <vector>

#ifdef _DEBUG
#define new DEBUG_NEW
#endif

const int TIMER_RECORD_ID		= 1001;
const int TIMER_RECORD_VALUE	= 10*60*1000; // 定时器10分钟

const int TIMER_GET_CONNSTATUS_ID		= 1002;
const int TIMER_GET_CONNSTATUS_VALUE	= 5*1000; // 定时器15秒


//*********************************************************
// CLoginInfo类
CLoginInfo::CLoginInfo()
{
	memset(m_szIP, 0, sizeof(m_szIP) );
	m_nPort	= 0;
	memset(m_szUserName, 0, sizeof(m_szUserName) );
	memset(m_szPwd, 0, sizeof(m_szPwd) );
	m_lHandle = 0;

	m_nCurSize	= 0;
	m_bOffline  = false;
	m_bOutputRealResult = false;
}

CLoginInfo::~CLoginInfo( )
{

}

void CLoginInfo::SetLoginInfo(LPCTSTR strIP, int nPort, LPCTSTR strUserName, LPCTSTR strPwd, long lHandle )
{
	strcpy_s(m_szIP, sizeof(m_szIP), strIP );
	m_nPort = nPort;
	strcpy_s(m_szUserName, sizeof(m_szUserName), strUserName );
	strcpy_s(m_szPwd, sizeof(m_szPwd), strPwd );
	m_lHandle = lHandle;
}

LPCTSTR CLoginInfo::GetIP()
{
	return m_szIP;
}

int CLoginInfo::GetPort()
{
	return m_nPort;
}

LPCTSTR CLoginInfo::GetUserName( )
{
	return m_szUserName;
}

LPCTSTR CLoginInfo::GetPwd()
{
	return m_szPwd;
}

long CLoginInfo::GetHandle( )
{
	return m_lHandle;
}

void CLoginInfo::SetCurSize(int nCurSize)
{
	m_nCurSize = nCurSize;
}

int CLoginInfo::GetCurSize( )
{
	return m_nCurSize;
}

void CLoginInfo::SetIsOffline( bool bOffline )
{
	m_bOffline = bOffline;
}

bool CLoginInfo::GetIsOffline( )
{
	return m_bOffline;
}

void CLoginInfo::SetIsOutputRealResult( bool bOutputRealResult)
{
	m_bOutputRealResult = bOutputRealResult;
}

bool CLoginInfo::GetIsOutputRealResult( )
{
	return m_bOutputRealResult;
}

void CLoginInfo::SetIsLoadOffline( bool bLoad )
{
	m_bLoadOffline = bLoad;
}

bool CLoginInfo::GetIsLoadOffline( )
{
	return m_bLoadOffline;
}

// CAboutDlg dialog used for App About

class CAboutDlg : public CDialog
{
public:
	CAboutDlg();

// Dialog Data
	enum { IDD = IDD_ABOUTBOX };

	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

// Implementation
protected:
	DECLARE_MESSAGE_MAP()
};

CAboutDlg::CAboutDlg() : CDialog(CAboutDlg::IDD)
{
}

void CAboutDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}

BEGIN_MESSAGE_MAP(CAboutDlg, CDialog)
END_MESSAGE_MAP()


// CVZLPRClientDemoDlg dialog




CVZLPRClientDemoDlg::CVZLPRClientDemoDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CVZLPRClientDemoDlg::IDD, pParent)
	, m_strPort(_T(""))
	, m_strUserName(_T(""))
	, m_strPwd(_T(""))
	, m_strSavePath(_T(""))
	, m_bOpen(FALSE)
	, m_strLblResult(_T(""))
	, m_bkColor(0x00777777)
	, m_borderColor(0x00333333)
	, m_activeBorderColor(0x0000FF00)
	, m_bSetOffline(FALSE)
	, m_bSaveOfflineData(FALSE)
	, m_bOutputRealtimeResult(FALSE)
	, m_bPlateImgOpen(FALSE)
	, m_sPlateImgPath(_T(""))
{
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);

	memset(m_strModuleDir, 0, MAX_PATH);
	memset(m_strModuleName, 0, MAX_PATH);
}

void CVZLPRClientDemoDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_VZLPRCLIENTCTRLCTRL1, m_vzLPRClientCtrl);
	DDX_Control(pDX, IDC_IPADDR, m_deviceIP);
	DDX_Text(pDX, IDC_EDIT_PORT, m_strPort);
	DDX_Text(pDX, IDC_EDIT_USERNAME, m_strUserName);
	DDX_Text(pDX, IDC_EDIT_PWD, m_strPwd);
	DDX_Control(pDX, IDC_LIST_DEVICE, m_lstDevice);
	DDX_Text(pDX, IDC_EDIT_PATH, m_strSavePath);
	DDX_Check(pDX, IDC_CHK_OPEN, m_bOpen);
	DDX_Control(pDX, IDC_PIC_PLATE, m_sPicPlate);
	DDX_Text(pDX, IDC_LBL_RESULT, m_strLblResult);
	DDX_Control(pDX, IDC_CMB_STYLE, m_cmbStyle);
	DDX_Control(pDX, IDC_CMB_BOTE, m_cmbBote);
	DDX_Check(pDX, IDC_CHK_SET_OFFLINE, m_bSetOffline);
	DDX_Control(pDX, IDC_BTN_START_RECORD, m_btnStartRecord);
	DDX_Control(pDX, IDC_BTN_STOP_RECORD, m_btnStopRecord);
	DDX_Check(pDX, IDC_CHK_OFFLINE_DATA, m_bSaveOfflineData);
	DDX_Check(pDX, IDC_CHK_OUTPUT_REALTIME_RESULT, m_bOutputRealtimeResult);
	DDX_Control(pDX, IDC_CMB_SIZE, m_cmbSize);
	DDX_Control(pDX, IDC_CMB_PROV, m_cmbProv);
	DDX_Control(pDX, IDC_LIST1, m_lstFindDevice);
	DDX_Check(pDX, IDC_CHK_OPEN2, m_bPlateImgOpen);
	DDX_Text(pDX, IDC_EDIT_PATH2, m_sPlateImgPath);
	DDX_Control(pDX, IDC_CMB_IO, m_cmbIO);
}

BEGIN_MESSAGE_MAP(CVZLPRClientDemoDlg, CDialog)
	ON_WM_SYSCOMMAND()
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	//}}AFX_MSG_MAP
	ON_WM_TIMER()
	ON_BN_CLICKED(IDC_BTN_OPEN, &CVZLPRClientDemoDlg::OnBnClickedBtnOpen)
	ON_BN_CLICKED(IDC_BTN_CLOSE, &CVZLPRClientDemoDlg::OnBnClickedBtnClose)
	ON_BN_CLICKED(IDC_BTN_PLAY, &CVZLPRClientDemoDlg::OnBnClickedBtnPlay)
	ON_BN_CLICKED(IDC_BTN_STOP, &CVZLPRClientDemoDlg::OnBnClickedBtnStop)
	ON_BN_CLICKED(IDC_CHK_OPEN, &CVZLPRClientDemoDlg::OnBnClickedChkOpen)
	ON_MESSAGE(WM_PLATE_MESSAGE, &CVZLPRClientDemoDlg::OnPlateMessage) 
	ON_MESSAGE(WM_SHOW_IMG_MESSAGE, &CVZLPRClientDemoDlg::OnShowImgMessage) 

	ON_BN_CLICKED(IDC_BTN_IMG, &CVZLPRClientDemoDlg::OnBnClickedBtnImg)
	ON_BN_CLICKED(IDC_BTN_DIR, &CVZLPRClientDemoDlg::OnBnClickedBtnDir)
	ON_CBN_SELCHANGE(IDC_CMB_STYLE, &CVZLPRClientDemoDlg::OnCbnSelchangeCmbStyle)
	ON_BN_CLICKED(IDC_BTN_IO_OPEN, &CVZLPRClientDemoDlg::OnBnClickedBtnIoOpen)
	ON_BN_CLICKED(IDC_BTN_485, &CVZLPRClientDemoDlg::OnBnClickedBtn485)
	ON_BN_CLICKED(IDC_BTN_Wlist, &CVZLPRClientDemoDlg::OnBnClickedBtnWlist)
	ON_BN_CLICKED(IDC_BTN_SetBkColor, &CVZLPRClientDemoDlg::OnBnClickedBtnSetbkcolor)
	ON_BN_CLICKED(IDC_BTN_SETBORDERCOLOR, &CVZLPRClientDemoDlg::OnBnClickedBtnSetbordercolor)
	ON_BN_CLICKED(IDC_BTN_SETACTIVEBORDERCOLOR, &CVZLPRClientDemoDlg::OnBnClickedBtnSetactivebordercolor)
	ON_WM_MOVE()
	ON_WM_CLOSE()
	ON_BN_CLICKED(IDC_BTN_PLATE_QUERY, &CVZLPRClientDemoDlg::OnBnClickedBtnPlateQuery)
	ON_BN_CLICKED(IDC_CHK_SET_OFFLINE, &CVZLPRClientDemoDlg::OnBnClickedChkSetOffline)
	ON_NOTIFY(HDN_ITEMCLICK, 0, &CVZLPRClientDemoDlg::OnHdnItemclickListDevice)
	ON_BN_CLICKED(IDC_BTN_START_RECORD, &CVZLPRClientDemoDlg::OnBnClickedBtnStartRecord)
	ON_BN_CLICKED(IDC_BTN_STOP_RECORD, &CVZLPRClientDemoDlg::OnBnClickedBtnStopRecord)
	ON_NOTIFY(NM_CLICK, IDC_LIST_DEVICE, &CVZLPRClientDemoDlg::OnNMClickListDevice)
	ON_BN_CLICKED(IDC_CHK_OFFLINE_DATA, &CVZLPRClientDemoDlg::OnBnClickedChkOfflineData)
	ON_BN_CLICKED(IDC_CHK_OUTPUT_REALTIME_RESULT, &CVZLPRClientDemoDlg::OnBnClickedChkOutputRealtimeResult)
	ON_CBN_SELCHANGE(IDC_CMB_SIZE, &CVZLPRClientDemoDlg::OnCbnSelchangeCmbSize)
	ON_BN_CLICKED(IDC_BTN_LED_CTL, &CVZLPRClientDemoDlg::OnBnClickedBtnLedCtl)
	ON_CBN_SELCHANGE(IDC_CMB_PROV, &CVZLPRClientDemoDlg::OnCbnSelchangeCmbProv)
	ON_BN_CLICKED(IDC_BTN_DEVICENUM, &CVZLPRClientDemoDlg::OnBnClickedBtnDevicenum)
	ON_BN_CLICKED(IDC_BTN_GETVERSION, &CVZLPRClientDemoDlg::OnBnClickedBtnGetversion)
	
	ON_BN_CLICKED(IDC_BUTTON1, &CVZLPRClientDemoDlg::OnBnClickedButton1)
	ON_BN_CLICKED(IDC_BUTTON2, &CVZLPRClientDemoDlg::OnBnClickedButton2)
	ON_NOTIFY(NM_CLICK, IDC_LIST1, &CVZLPRClientDemoDlg::OnNMClickList1)
	ON_BN_CLICKED(IDC_CHK_OPEN2, &CVZLPRClientDemoDlg::OnBnClickedChkOpen2)
	ON_BN_CLICKED(IDC_BTN_STOP2, &CVZLPRClientDemoDlg::OnBnClickedBtnStop2)
	ON_BN_CLICKED(IDC_BTN_DIR2, &CVZLPRClientDemoDlg::OnBnClickedBtnDir2)
	ON_BN_CLICKED(IDC_BTN_IMG2, &CVZLPRClientDemoDlg::OnBnClickedBtnImg2)
	ON_BN_CLICKED(IDC_BTN_SAVE_LAST_IMG, &CVZLPRClientDemoDlg::OnBnClickedBtnSaveLastImg)
	ON_BN_CLICKED(IDC_BTN_STATUS, &CVZLPRClientDemoDlg::OnBnClickedBtnStatus)
	ON_BN_CLICKED(IDC_BTN_SET_PWD, &CVZLPRClientDemoDlg::OnBnClickedBtnSetPwd)
END_MESSAGE_MAP()


// CVZLPRClientDemoDlg message handlers

BOOL CVZLPRClientDemoDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// Add "About..." menu item to system menu.

	// IDM_ABOUTBOX must be in the system command range.
	ASSERT((IDM_ABOUTBOX & 0xFFF0) == IDM_ABOUTBOX);
	ASSERT(IDM_ABOUTBOX < 0xF000);

	CMenu* pSysMenu = GetSystemMenu(FALSE);
	if (pSysMenu != NULL)
	{
		CString strAboutMenu;
		strAboutMenu.LoadString(IDS_ABOUTBOX);
		if (!strAboutMenu.IsEmpty())
		{
			pSysMenu->AppendMenu(MF_SEPARATOR);
			pSysMenu->AppendMenu(MF_STRING, IDM_ABOUTBOX, strAboutMenu);
		}
	}

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon

	GetModuleFileName(NULL, m_strModuleDir, MAX_PATH);
	*strrchr(m_strModuleDir, '\\') = 0;

	m_lstDevice.InsertColumn( 0, "设备IP",   LVCFMT_LEFT, 290  );
	m_lstFindDevice.InsertColumn( 0, "设备IP",   LVCFMT_LEFT, 205  );

	m_strPort		= _T("80");
	m_strUserName	= _T("admin");
	m_strPwd		= _T("admin");
	UpdateData(FALSE);

	m_cmbStyle.AddString( _T("1") );
	m_cmbStyle.AddString( _T("4") );
	m_cmbStyle.AddString( _T("9") );
	m_cmbStyle.AddString( _T("16") );

	m_cmbStyle.SetCurSel( 1 );

	m_cmbBote.AddString( _T("2400") );
	m_cmbBote.AddString( _T("4800") );
	m_cmbBote.AddString( _T("9600") );
	m_cmbBote.AddString( _T("15200") );
	m_cmbBote.SetCurSel( 2 );

	m_cmbSize.AddString(_T("无"));
	m_cmbSize.AddString(_T("CIF"));
	m_cmbSize.AddString(_T("D1"));
	m_cmbSize.AddString(_T("720P"));
	m_cmbSize.SetCurSel(0);

	m_cmbIO.AddString("OUT1");
	m_cmbIO.AddString("OUT2");
	m_cmbIO.AddString("OUT3");
	m_cmbIO.AddString("OUT4");
	m_cmbIO.SetCurSel(0);

	// TODO: Add extra initialization here
	m_vzLPRClientCtrl.SetWindowStyle( 4 );

	m_vzLPRClientCtrl.VzLPRSetCtlColor(m_bkColor,m_borderColor,m_activeBorderColor);

	string sAppPath = CVzFile::GetAppPath( );
	
	string sConfigPath = sAppPath;
	sConfigPath.append("\\config.txt");

	char szDirPath[MAX_PATH] = {0};

	CVzFile::ReadTextFile( sConfigPath.c_str(), szDirPath, MAX_PATH - 1 );

	if ( strlen(szDirPath) == 0 )
	{
		string sPicPath		= sAppPath;
		sPicPath.append("\\PlatePic");

		if ( !CVzFile::IsPathExist(sPicPath.c_str())  )
		{
			::CreateDirectory(sPicPath.c_str(), NULL);
		}

		m_strSavePath = sPicPath.c_str();
		m_sPlateImgPath = sPicPath.c_str();
	}
	else
	{
		m_strSavePath = szDirPath;
		m_sPlateImgPath = szDirPath;
	}

	m_lprHandle = 0;

	m_strWinIndex = _T("0");
	UpdateData( FALSE );

	SetTimer(TIMER_RECORD_ID, TIMER_RECORD_VALUE, NULL);

	SetTimer(TIMER_GET_CONNSTATUS_ID, TIMER_GET_CONNSTATUS_VALUE, NULL);

	// m_vzLPRClientCtrl.ShowWindow( SW_HIDE );

	return TRUE;  // return TRUE  unless you set the focus to a control
}

void CVZLPRClientDemoDlg::OnSysCommand(UINT nID, LPARAM lParam)
{
	if ((nID & 0xFFF0) == IDM_ABOUTBOX)
	{
		CAboutDlg dlgAbout;
		dlgAbout.DoModal();
	}
	else
	{
		CDialog::OnSysCommand(nID, lParam);
	}
}

// If you add a minimize button to your dialog, you will need the code below
//  to draw the icon.  For MFC applications using the document/view model,
//  this is automatically done for you by the framework.

void CVZLPRClientDemoDlg::OnPaint()
{
	if (IsIconic())
	{
		CPaintDC dc(this); // device context for painting

		SendMessage(WM_ICONERASEBKGND, reinterpret_cast<WPARAM>(dc.GetSafeHdc()), 0);

		// Center icon in client rectangle
		int cxIcon = GetSystemMetrics(SM_CXICON);
		int cyIcon = GetSystemMetrics(SM_CYICON);
		CRect rect;
		GetClientRect(&rect);
		int x = (rect.Width() - cxIcon + 1) / 2;
		int y = (rect.Height() - cyIcon + 1) / 2;

		// Draw the icon
		dc.DrawIcon(x, y, m_hIcon);
	}
	else
	{
		CDialog::OnPaint();
	}
}

// The system calls this function to obtain the cursor to display while the user drags
//  the minimized window.
HCURSOR CVZLPRClientDemoDlg::OnQueryDragIcon()
{
	return static_cast<HCURSOR>(m_hIcon);
}


void CVZLPRClientDemoDlg::OnBnClickedBtnOpen()
{
	UpdateData(TRUE);

	CString strDeviceIP;
	m_deviceIP.GetWindowText(strDeviceIP);

	if( strDeviceIP == _T("0.0.0.0") )
	{
		MessageBox(_T("请输入设备IP"), _T("提示"), MB_OK );
		return;
	}

	BOOL bOpened = GetIsOpened( strDeviceIP.GetBuffer(0) );
	if ( bOpened )
	{
		CString strMsg;
		strMsg.Format( _T("%s 设备已经打开."), strDeviceIP );

		MessageBox( strMsg, _T("提示"), MB_OK );
		return;
	}

	int nPort = atoi( m_strPort.GetBuffer(0) );

	long lHandle = m_vzLPRClientCtrl.VzLPRClientOpen( strDeviceIP.GetBuffer(0), nPort, m_strUserName.GetBuffer(0), m_strPwd.GetBuffer(0) );
	// m_vzLPRClientCtrl.SetIsSaveFullImage(lHandle, TRUE, m_strSavePath.GetBuffer(0) );

	if ( lHandle > 0 )
	{
		int nIndex = m_lstDevice.InsertItem(0, strDeviceIP);
		m_lstDevice.SetItemState(nIndex, LVNI_FOCUSED | LVIS_SELECTED, LVNI_FOCUSED | LVIS_SELECTED); 
	
		CLoginInfo *pLoginInfo = new CLoginInfo();
		pLoginInfo->SetLoginInfo(strDeviceIP.GetBuffer(0), nPort, m_strUserName.GetBuffer(0), m_strPwd.GetBuffer(0), lHandle);

		m_lstDevice.SetItemData( nIndex, (DWORD_PTR)pLoginInfo );
		m_lprHandle = lHandle;

		m_vzLPRClientCtrl.VzLPRSetIsSaveLastImage(lHandle, TRUE, TRUE);

		LoadProv( lHandle );
	}
	else
	{
		MessageBox(_T("打开设备失败，请检查设备登录信息"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::LoadProv( long lprHandle )
{
	CString strCurProv = m_vzLPRClientCtrl.VzLPRGetCurProvince(lprHandle);//获得当前的默认省份
	CString strProvs   = m_vzLPRClientCtrl.VzLPRGetSupportedProvinces(lprHandle);//获取所有支持的省份简称构成的字符串


	m_cmbProv.ResetContent( );

	int nCurSel = 0;
	m_cmbProv.AddString("无");

	char szProvs[128] = {0};
	strcpy(szProvs, strProvs.GetBuffer(0) );
	char szProvince[3] = {0};

	int nIndex = 0;

	int nLength = strlen(szProvs);
	for( int i = 0 ; i < nLength; i += 2 )
	{
		nIndex++;

		memset( szProvince, 0, sizeof(szProvince) );
		strncpy( szProvince, szProvs + i, 2 );

		m_cmbProv.AddString( szProvince );

		if( strCurProv == szProvince )
		{
			nCurSel = nIndex;
		}
	}

	m_cmbProv.SetCurSel(nCurSel);
}

void CVZLPRClientDemoDlg::OnBnClickedBtnClose()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );
		m_vzLPRClientCtrl.VzLPRClientClose( lHandle );

		CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nCurSel));
		if( pLoginInfo != NULL )
		{
			delete pLoginInfo;
			pLoginInfo = NULL;
		}

		m_cmbProv.ResetContent( );

		m_lstDevice.DeleteItem( nCurSel );
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

long CVZLPRClientDemoDlg::GetItemHandle( int nIndex )
{
	long lHandle = 0;

	if( nIndex >=0  )
	{
		CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nIndex));
		if ( pLoginInfo != NULL )
		{
			lHandle = pLoginInfo->GetHandle();
		}
	}

	return lHandle;
}

void CVZLPRClientDemoDlg::OnBnClickedBtnPlay()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		int nCurWin = m_vzLPRClientCtrl.GetCurrentWin( );
		m_vzLPRClientCtrl.VzLPRClientStartPlay( lHandle, nCurWin );
		m_vzLPRClientCtrl.VzLPRSerialStart(0, 0);
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::OnBnClickedBtnStop()
{
	int nCurWin = m_vzLPRClientCtrl.GetCurrentWin( );
	m_vzLPRClientCtrl.VzLPRSerialStop(0);
	m_vzLPRClientCtrl.VzLPRClientStopPlay( nCurWin );
}

BEGIN_EVENTSINK_MAP(CVZLPRClientDemoDlg, CDialog)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 7, CVZLPRClientDemoDlg::OnLPRPlateInfoOutVzlprclientctrlctrl1, VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I4 VTS_I2 VTS_I2 VTS_BSTR VTS_I2)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 8, CVZLPRClientDemoDlg::OnLPRPlateImgOutVzlprclientctrlctrl1, VTS_BSTR VTS_BSTR VTS_I2)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 9, CVZLPRClientDemoDlg::OnLPRCommonNotifyVzlprclientctrlctrl1, VTS_BSTR VTS_I2)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 10, CVZLPRClientDemoDlg::OnLPRPlateInfoOutExVzlprclientctrlctrl1, VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I4 VTS_I2 VTS_I2 VTS_BSTR VTS_BSTR VTS_I2)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 11, CVZLPRClientDemoDlg::OnLPRWlistInfoOutVzlprclientctrlctrl1, VTS_I4 VTS_BSTR VTS_BOOL VTS_BSTR VTS_BOOL VTS_BOOL VTS_BSTR VTS_I4 VTS_BSTR VTS_BSTR)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 13, CVZLPRClientDemoDlg::OnLPROfflinePlateInfoOutVzlprclientctrlctrl1, VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I2 VTS_I4 VTS_I2 VTS_I2 VTS_BSTR VTS_BSTR VTS_I2)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 16, CVZLPRClientDemoDlg::OnLPRSerialRecvDataVzlprclientctrlctrl1, VTS_I4 VTS_BSTR VTS_BSTR VTS_I4)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 18, CVZLPRClientDemoDlg::DbClickVzlprclientctrlctrl1, VTS_NONE)
	ON_EVENT(CVZLPRClientDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 15, CVZLPRClientDemoDlg::OnLPRFindDeviceInfoOutVzlprclientctrlctrl1, VTS_BSTR VTS_I4 VTS_I4 VTS_I4 VTS_I4)
END_EVENTSINK_MAP()

void CVZLPRClientDemoDlg::OnLPRPlateInfoOutVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short type, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR ip, short resultType)
{
	CString strResult;
	strResult.Format( _T("%s %d %s"), license, resultType, ip );
	m_strLblResult = strResult;

	PostMessage(WM_PLATE_MESSAGE, NULL, NULL );
}
void CVZLPRClientDemoDlg::OnLPRPlateImgOutVzlprclientctrlctrl1(LPCTSTR imagePath, LPCTSTR ip, short resultType)
{
	m_strFullImgPath = imagePath;
	PostMessage(WM_SHOW_IMG_MESSAGE, NULL, NULL );
}

void CVZLPRClientDemoDlg::OnBnClickedChkOpen()
{
	UpdateData(TRUE);

	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );
		int nSizeCur = m_cmbSize.GetCurSel();

		if ( m_bOpen )
		{
			m_vzLPRClientCtrl.SetIsSaveFullImage(lHandle, TRUE, m_strSavePath.GetBuffer(0) );
			m_vzLPRClientCtrl.VzLPRSetOutputImgMode(lHandle, nSizeCur, 90);
		}
		else
		{
			m_vzLPRClientCtrl.SetIsSaveFullImage(lHandle, FALSE, "" );
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

LRESULT CVZLPRClientDemoDlg::OnPlateMessage(WPARAM wParam, LPARAM lParam)
{
	UpdateData(FALSE);
	return 0;
}

LRESULT CVZLPRClientDemoDlg::OnShowImgMessage(WPARAM wParam, LPARAM lParam)
{
	if( m_sPicPlate.GetSafeHwnd() == NULL || m_strFullImgPath.GetLength() == 0 )
	{
		return -1;
	}

	CDC *pDC = m_sPicPlate.GetDC( );

	RECT rc;
	m_sPicPlate.GetClientRect( &rc );

	CImage img;
	HRESULT hr = img.Load( m_strFullImgPath );
	if( SUCCEEDED(hr) )
	{
		RECT rcSrc;
		rcSrc.left = 0;
		rcSrc.top = 0;
		rcSrc.right = img.GetWidth();
		rcSrc.bottom = img.GetHeight();

		SetStretchBltMode(pDC->m_hDC,COLORONCOLOR);

		img.Draw( pDC->m_hDC, rc, rcSrc);
		ReleaseDC( pDC ); 
	}

	return 0;
}
void CVZLPRClientDemoDlg::OnBnClickedBtnImg()
{
	string sPicPath = CVzFile::GetAppPath( );
	sPicPath.append("\\test.jpg");

	int nCurWin = m_vzLPRClientCtrl.GetCurrentWin();
	nCurWin = (nCurWin < 0 ) ? 0 : nCurWin;

	// TODO: Add your control notification handler code here
	BOOL ret = m_vzLPRClientCtrl.VzLPRClientCaptureImg( nCurWin, sPicPath.c_str() );
	if ( ret )
	{
		char szMsg[1024] = {0};
		sprintf_s( szMsg, sizeof(szMsg), "抓图成功 %s", sPicPath.c_str() );
		MessageBox( szMsg, "提示", MB_OK );
	}
	else
	{
		MessageBox( "抓图失败", "提示", MB_OK  );
	}

	// m_vzLPRClientCtrl.VzLPRClientForceTrigger( 0 );
	// m_vzLPRClientCtrl.VzLPRClientStopPlay
	// char byData[] = "FF2C4D003B";
	/*byData[0] = '1';
	byData[1] = '2';
	byData[2] = '3';
	byData[3] = 12;
	byData[4] = 45;
	byData[5] = -1;
	byData[6] = 0;
	byData[7] = 0;
	byData[8]  = '4';
	byData[9]  = '5';
	byData[10] = '6';*/

	// m_vzLPRClientCtrl.VzLPRSerialSend( 0, byData, strlen(byData) );
}

void CVZLPRClientDemoDlg::OnBnClickedBtnDir()
{
	// TODO: Add your control notification handler code here
	TCHAR pszPath[MAX_PATH] = {0};  
    BROWSEINFO bi;   
    bi.hwndOwner      = this->GetSafeHwnd();  
    bi.pidlRoot       = NULL;  
    bi.pszDisplayName = NULL;   
    bi.lpszTitle      = TEXT("请选择文件夹");   
    bi.ulFlags        = BIF_RETURNONLYFSDIRS | BIF_STATUSTEXT;  
    bi.lpfn           = NULL;   
    bi.lParam         = 0;  
    bi.iImage         = 0;   
  
    LPITEMIDLIST pidl = SHBrowseForFolder(&bi);  
    if (pidl == NULL)  
    {  
        return;  
    }  
  
    if (SHGetPathFromIDList(pidl, pszPath))  
    {  
        m_strSavePath =   pszPath;
		UpdateData( FALSE );

		string sAppPath = CVzFile::GetAppPath( );
		sAppPath.append("\\config.txt");

		CVzFile::WriteTxtFile( sAppPath.c_str(), pszPath );
    }  

}

void CVZLPRClientDemoDlg::OnCbnSelchangeCmbStyle()
{
	// TODO: Add your control notification handler code here
	int nCurSel = m_cmbStyle.GetCurSel();

	switch( nCurSel )
	{
	case 0:
		m_vzLPRClientCtrl.SetWindowStyle( 1 );
		break;

	case 1:
		m_vzLPRClientCtrl.SetWindowStyle( 4 );
		break;

	case 2:
		m_vzLPRClientCtrl.SetWindowStyle( 9 );
		break;

	case 3:
		m_vzLPRClientCtrl.SetWindowStyle( 16 );
		break;

	default:
		break;
	}
}

BOOL CVZLPRClientDemoDlg::GetIsOpened( LPCSTR strIP )
{
	BOOL bOpened = FALSE;

	int nRowCount = this->m_lstDevice.GetItemCount( );
	for ( int i = 0; i < nRowCount; i++ )
	{
		CString strDeviceIP = m_lstDevice.GetItemText(i, 0 );
		if ( strDeviceIP == strIP )
		{
			bOpened = TRUE;
			break;
		}
	}

	return bOpened;
}

int CVZLPRClientDemoDlg::GetSeleIndex( )
{
	int nCurSel = -1;

	for(int i=0; i< m_lstDevice.GetItemCount(); i++)
	{
	   if( m_lstDevice.GetItemState(i, LVIS_SELECTED) == LVIS_SELECTED )
	   {
			nCurSel = i;
			break;
	   }
	}

	return nCurSel;
}

void CVZLPRClientDemoDlg::OnBnClickedBtnIoOpen()
{
	// TODO: Add your control notification handler code here
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		int nCurIO = m_cmbIO.GetCurSel();
		long lHandle = GetItemHandle( nCurSel );
		int ret = m_vzLPRClientCtrl.VzLPRSetIOOutputAuto(lHandle, nCurIO, 500);
	}
}

// 设备断线的回调
void CVZLPRClientDemoDlg::OnLPRCommonNotifyVzlprclientctrlctrl1(LPCTSTR ip, short nNotify)
{
}

void CVZLPRClientDemoDlg::OnLPRPlateInfoOutExVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
																  short resultType)
{

}

void CVZLPRClientDemoDlg::OnBnClickedBtn485()
{
	// TODO: Add your control notification handler code here

	char szData[32] = {0};
	strcat(szData, "FF3D2C5B");

	//int nPort = 9600;

	//int nCurSel = m_cmbBote.GetCurSel();
	//switch ( nCurSel )
	//{
	//case 0:
	//	nPort = 2400;
	//	break;

	//case 1:
	//	nPort = 4800;
	//	break;

	//case 2:
	//	nPort = 9600;
	//	break;

	//case 3:
	//	nPort = 15200;
	//	break;

	//default:
	//	nPort = 9600;
	//	break;
	//}

	
	m_vzLPRClientCtrl.VzLPRSerialSend(0, szData, strlen(szData) );
	
}

// 白名单功能
void CVZLPRClientDemoDlg::OnBnClickedBtnWlist()
{
	// if( m_lprHandle > 0 )
	// {
		// m_vzLPRClientCtrl.VzLPRClientAddWlist("test", 0, TRUE ,"2014-11-09 12:01:01", FALSE, FALSE, "" ,m_lprHandle );
		// m_vzLPRClientCtrl.VzLPRClientQueryWlistByPlate("津", m_lprHandle);
		// m_vzLPRClientCtrl.VzLPRClientDeleteWlist("test", m_lprHandle);

		// m_vzLPRClientCtrl.VzLPRClientUpdateWlist("津B451TB", 0, TRUE, "2014-12-05 12:05:09", FALSE, FALSE, "", 1, m_lprHandle);
		// m_vzLPRClientCtrl.VzLPRClientDeleteWlistCustom(4, m_lprHandle);
	// }

	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nCurSel));
		if ( pLoginInfo != NULL )
		{
			CDlgWlist dlgWlist;
			dlgWlist.SetDeviceIP( pLoginInfo->GetIP(), pLoginInfo->GetPort(), pLoginInfo->GetUserName(), pLoginInfo->GetPwd() );
			dlgWlist.DoModal();
		}
	}
	else
	{
		MessageBox( _T("请选择一台设备"), _T("提示"), MB_OK );
	}

	
}
void CVZLPRClientDemoDlg::OnLPRWlistInfoOutVzlprclientctrlctrl1(long lVehicleID, LPCTSTR strPlateID, BOOL bEnable, LPCTSTR strOverdule, BOOL bUsingTimeSeg, BOOL bAlarm, LPCTSTR strReserved, long lCustomerID, LPCTSTR strCustomerName, LPCTSTR strCustomerCode)
{
	// TODO: Add your message handler code here
	// int mm = 0;
}

void CVZLPRClientDemoDlg::OnBnClickedBtnSetbkcolor()
{
	COLORREF color = RGB(255, 0, 0);      // 颜色对话框的初始颜色为红色  
    CColorDialog colorDlg(color);         // 构造颜色对话框，传入初始颜色值   
  
    if (IDOK == colorDlg.DoModal())       // 显示颜色对话框，并判断是否点击了“确定”   
    {   
        color = colorDlg.GetColor();
		m_bkColor = color;
		m_vzLPRClientCtrl.VzLPRSetCtlColor(m_bkColor,m_borderColor,m_activeBorderColor);
	}

}

void CVZLPRClientDemoDlg::OnBnClickedBtnSetbordercolor()
{
	COLORREF color = RGB(255, 0, 0);      // 颜色对话框的初始颜色为红色  
    CColorDialog colorDlg(color);         // 构造颜色对话框，传入初始颜色值   
  
    if (IDOK == colorDlg.DoModal())       // 显示颜色对话框，并判断是否点击了“确定”   
    {   
        color = colorDlg.GetColor();
		m_borderColor = color;
		m_vzLPRClientCtrl.VzLPRSetCtlColor(m_bkColor,m_borderColor,m_activeBorderColor);
	}
}

void CVZLPRClientDemoDlg::OnBnClickedBtnSetactivebordercolor()
{
	COLORREF color = RGB(255, 0, 0);      // 颜色对话框的初始颜色为红色  
    CColorDialog colorDlg(color);         // 构造颜色对话框，传入初始颜色值   
  
    if (IDOK == colorDlg.DoModal())       // 显示颜色对话框，并判断是否点击了“确定”   
    {   
        color = colorDlg.GetColor();
		m_activeBorderColor = color;
		m_vzLPRClientCtrl.VzLPRSetCtlColor(m_bkColor,m_borderColor,m_activeBorderColor);
	}
}

void CVZLPRClientDemoDlg::OnMove(int x, int y)
{
	CDialog::OnMove(x, y);

	// TODO: Add your message handler code here
	OnShowImgMessage(NULL, NULL);
}

// 车牌查询的功能
void CVZLPRClientDemoDlg::OnBnClickedBtnPlateQuery()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nCurSel));
		if ( pLoginInfo != NULL )
		{
			CDlgPlateQuery dlg;
			dlg.SetDeviceIP( pLoginInfo->GetIP(), pLoginInfo->GetPort(), pLoginInfo->GetUserName(), pLoginInfo->GetPwd() );
			dlg.DoModal();
		}
		
	}
	else
	{
		MessageBox( _T("请选择一台设备"), _T("提示"), MB_OK );
	}
}

CLoginInfo* CVZLPRClientDemoDlg::GetCurItemInfo( )
{
	CLoginInfo *pLoginInfo = NULL;

	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nCurSel));
	}

	return pLoginInfo;
}

void CVZLPRClientDemoDlg::OnBnClickedChkSetOffline()
{
	UpdateData(TRUE);

	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );
		if( m_bSetOffline )
		{
			m_vzLPRClientCtrl.VzLPRSetOfflineCheck(	lHandle );
		}
		else
		{
			m_vzLPRClientCtrl.VzLPRCancelOfflineCheck(lHandle );
		}

		CLoginInfo *pInfo = GetCurItemInfo( );
		if ( pInfo != NULL )
		{
			pInfo->SetIsOffline( m_bSetOffline );
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}
void CVZLPRClientDemoDlg::OnHdnItemclickListDevice(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMHEADER phdr = reinterpret_cast<LPNMHEADER>(pNMHDR);
	// TODO: Add your control notification handler code here
	*pResult = 0;
}

void CVZLPRClientDemoDlg::OnBnClickedBtnStartRecord()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		CString strDevice = m_lstDevice.GetItemText(nCurSel, 0);

		iRecordVideo(lHandle, strDevice);

		m_vecRecordHandle.push_back(lHandle);

		m_btnStartRecord.EnableWindow(FALSE);
		m_btnStopRecord.EnableWindow(TRUE);
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::OnBnClickedBtnStopRecord()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );
		m_vzLPRClientCtrl.VzLPRStopSaveRealData(lHandle);

		vector<int>::iterator it = std::find(m_vecRecordHandle.begin(), m_vecRecordHandle.end(), lHandle);
		if( it != m_vecRecordHandle.end() )
		{
			m_vecRecordHandle.erase(it);
		}

		m_btnStartRecord.EnableWindow(TRUE);
		m_btnStopRecord.EnableWindow(FALSE);
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

// 选中列表的事件
void CVZLPRClientDemoDlg::OnNMClickListDevice(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMITEMACTIVATE pNMItemActivate = reinterpret_cast<NMITEMACTIVATE*>(pNMHDR);

	int nIndex = GetSeleIndex();
	if ( nIndex < 0 )
	{
		return;
	}
	
	long nLprHandle = GetItemHandle(nIndex);

	// 是否允许截图
	BOOL bSave = m_vzLPRClientCtrl.VzLPRGetIsSaveFullImg(nLprHandle);
	m_bOpen = bSave;
	m_bPlateImgOpen = m_oPlateImageEnableMap[nLprHandle];

	UpdateData(FALSE);

	vector<int>::iterator it = std::find(m_vecRecordHandle.begin(), m_vecRecordHandle.end(), nLprHandle);
	if( it != m_vecRecordHandle.end() )
	{
		m_btnStartRecord.EnableWindow(FALSE);
		m_btnStopRecord.EnableWindow(TRUE);
	}
	else
	{
		m_btnStartRecord.EnableWindow(TRUE);
		m_btnStopRecord.EnableWindow(FALSE);
	}

	LoadProv( nLprHandle );

	CLoginInfo *pInfo = GetCurItemInfo( );
	if ( pInfo != NULL )
	{
		m_cmbSize.SetCurSel( pInfo->GetCurSize() );

		m_bSaveOfflineData = pInfo->GetIsLoadOffline( );
		m_bOutputRealtimeResult = pInfo->GetIsOutputRealResult();
		m_bSetOffline = pInfo->GetIsOffline();

		UpdateData(FALSE);
	}
	
	*pResult = 0;
}

void CVZLPRClientDemoDlg::iRecordVideo(int lLPRHandle, CString strDevice )
{
	COleDateTime dtNow = COleDateTime::GetCurrentTime();

	char strFilePath[MAX_PATH] = {0};
	sprintf_s(strFilePath, MAX_PATH, "%s\\Video", m_strModuleDir );

	if(!PathIsDirectory(strFilePath))
	{
		CreateDirectory(strFilePath, NULL);
	}

	char szPath[MAX_PATH] = {0};
	sprintf_s(szPath, sizeof(szPath), "%s\\%s_%d%02d%02d%02d%02d%02d.avi", strFilePath, strDevice.GetBuffer(0), dtNow.GetYear(), dtNow.GetMonth(), dtNow.GetDay(),
		dtNow.GetHour(), dtNow.GetMinute(),dtNow.GetSecond() );

	m_vzLPRClientCtrl.VzLPRSaveRealData(lLPRHandle, szPath);
}

void CVZLPRClientDemoDlg::iReRecordAllVideo( )
{
	char szPath[MAX_PATH] = {0};

	int nSize = m_vecRecordHandle.size( );
	for ( int i = 0; i < nSize; i++ )
	{
		long lLPRClient = (long)m_vecRecordHandle[i];
		if ( lLPRClient != 0 )
		{
			CString strDevice = iGetDeviceItemText(lLPRClient);
			if ( strDevice != "" )
			{
				// 停止录像
				m_vzLPRClientCtrl.VzLPRStopSaveRealData( lLPRClient );

				// 重新录像
				iRecordVideo(lLPRClient, strDevice);
			}
		}
	}
}

CString CVZLPRClientDemoDlg::iGetDeviceItemText( long lLPRClient )
{
	CString strDevice;

	int nCount = m_lstDevice.GetItemCount( );
	for( int i = 0 ; i < nCount; i++ )
	{
		int nUserData = GetItemHandle(i);
		if( nUserData == lLPRClient )
		{
			strDevice = m_lstDevice.GetItemText(i, 0);
			break;
		}
	}

	return strDevice;
}

void CVZLPRClientDemoDlg::OnTimer(UINT_PTR nIDEvent)
{
	if( nIDEvent == TIMER_RECORD_ID )
	{
		// 定时全部重新录像
		iReRecordAllVideo( );
	}
	else if( nIDEvent == TIMER_GET_CONNSTATUS_ID )
	{
		// GetSaveCurConnStatus( );
	}

	CDialog::OnTimer(nIDEvent);
}

void CVZLPRClientDemoDlg::GetSaveCurConnStatus( )
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		CString strIP = iGetDeviceItemText(lHandle);
		int conn = m_vzLPRClientCtrl.VzLPRClientIsConnected(lHandle);

		COleDateTime dtNow = COleDateTime::GetCurrentTime();

		char szLog[128] = {0};
		sprintf(szLog, "%d-%02d-%02d %02d:%02d:%02d %s conn status: %d\n", dtNow.GetYear(), dtNow.GetMonth(), dtNow.GetDay(),
			dtNow.GetHour(), dtNow.GetMinute(), dtNow.GetSecond(), strIP.GetBuffer(0), conn);

		CVzFile::WriteTxtFileAppend("conn_status.txt", szLog);
	}
}

void CVZLPRClientDemoDlg::OnClose()
{
	KillTimer(TIMER_RECORD_ID);

	KillTimer(TIMER_GET_CONNSTATUS_ID);

	// 停止全部录像
	int nSize = m_vecRecordHandle.size( );
	for ( int i = 0; i < nSize; i++ )
	{
		long lLPRClient = (long)m_vecRecordHandle[i];
		if ( lLPRClient != 0 )
		{
			m_vzLPRClientCtrl.VzLPRStopSaveRealData( lLPRClient );

			m_vzLPRClientCtrl.VzLPRClientClose(lLPRClient);
		}
	}

	for(int i=0; i< m_lstDevice.GetItemCount(); i++)
	{
		CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(i));
		if( pLoginInfo != NULL )
		{
			delete pLoginInfo;
			pLoginInfo = NULL;
		}
	}

	CDialog::OnClose( );
}

void CVZLPRClientDemoDlg::OnBnClickedChkOfflineData()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		UpdateData(TRUE);

		if ( m_bSaveOfflineData )
		{
			m_vzLPRClientCtrl.VzLPRSetLoadOfflinePlate(lHandle, TRUE, TRUE);
		}
		else
		{
			m_vzLPRClientCtrl.VzLPRSetLoadOfflinePlate(lHandle, FALSE, FALSE);
		}

		CLoginInfo *pInfo = GetCurItemInfo( );
		if ( pInfo != NULL )
		{
			pInfo->SetIsLoadOffline( m_bSaveOfflineData );
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}
void CVZLPRClientDemoDlg::OnLPROfflinePlateInfoOutVzlprclientctrlctrl1(LPCTSTR license, LPCTSTR color, short colorIndex, short nType, short confidence, short bright, short nDirection, long time, short carBright, short carColor, LPCTSTR imgPath, LPCTSTR ip, 
																	   short resultType)
{
	// TODO: Add your message handler code here
	CString strResult;
	strResult.Format( _T("%s %s"), license, ip );
	m_strLblResult = strResult;

	PostMessage(WM_PLATE_MESSAGE, NULL, NULL );

	m_strFullImgPath = imgPath;
	PostMessage(WM_SHOW_IMG_MESSAGE, NULL, NULL );
}

void CVZLPRClientDemoDlg::OnLPRSerialRecvDataVzlprclientctrlctrl1(long lSerialHandle, LPCTSTR recvData, LPCTSTR ip, long lHandle)
{
	// TODO: Add your message handler code here
	int mm = 0;
}

void CVZLPRClientDemoDlg::OnBnClickedChkOutputRealtimeResult()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		UpdateData(TRUE);

		if ( m_bOutputRealtimeResult )
		{
			m_vzLPRClientCtrl.VzLPRSetIsOutputRealtimeResult(lHandle, TRUE);
		}
		else
		{
			m_vzLPRClientCtrl.VzLPRSetIsOutputRealtimeResult(lHandle, FALSE);
		}

		CLoginInfo *pInfo = GetCurItemInfo( );
		if ( pInfo != NULL )
		{
			pInfo->SetIsOutputRealResult( m_bOutputRealtimeResult );
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::DbClickVzlprclientctrlctrl1()
{
	// MessageBox(_T("双击窗口"), _T("提示"), MB_OK );
}

void CVZLPRClientDemoDlg::OnCbnSelchangeCmbSize()
{
	// TODO: Add your control notification handler code here
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		int nSizeCur = m_cmbSize.GetCurSel();
		m_vzLPRClientCtrl.VzLPRSetOutputImgMode(lHandle, nSizeCur, 90);

		CLoginInfo *pInfo = GetCurItemInfo( );
		if ( pInfo != NULL )
		{
			pInfo->SetCurSize(nSizeCur);
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::OnBnClickedBtnLedCtl()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		CDlgLedCtrl dlg;
		dlg.SetVzlprclientctrl(&m_vzLPRClientCtrl, lHandle);
		dlg.DoModal( );
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::OnCbnSelchangeCmbProv()
{
	int nSelect = m_cmbProv.GetCurSel();

	CString strCurProv;
	m_cmbProv.GetLBText(nSelect,strCurProv); 

	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );
		m_vzLPRClientCtrl.VzLPRPresetProvince(lHandle, strCurProv );
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::OnBnClickedBtnDevicenum()
{
	// TODO: Add your control notification handler code here
	CString nSerialNum = "";
	int nCurSel = GetSeleIndex();
	if (nCurSel >= 0)
	{
		long lHandle = GetItemHandle(nCurSel);
        nSerialNum = m_vzLPRClientCtrl.VzLPRGetSerialNumber(lHandle);
		if (nSerialNum != "" )
		{
			CString strMsg;
		    strMsg.Format(_T("本台设备序列号: %s "),nSerialNum);
			MessageBox(strMsg,_T("提示"),MB_OK);
		}
	} 
	else
	{
		MessageBox(_T("请选择一台设备"), _T("提示"), MB_OK );

	}
}

void CVZLPRClientDemoDlg::OnBnClickedBtnGetversion()
{
	// TODO: Add your control notification handler code here
	CString nVersion = "";
	nVersion = m_vzLPRClientCtrl.VzLPRGetVersion();
	if (nVersion != "")
	{
		CString strMsg;
		strMsg.Format(_T("OCX控件版本号: %s "),nVersion);
		MessageBox(strMsg,_T("提示"),MB_OK);
	}	
}



void CVZLPRClientDemoDlg::OnBnClickedButton1()
{
	m_vzLPRClientCtrl.VzLPRStopFindDevice();
	deleteDeviceInfo();
	m_vzLPRClientCtrl.VzLPRStartFindDevice();
}

void CVZLPRClientDemoDlg::OnBnClickedButton2()
{
	m_vzLPRClientCtrl.VzLPRStopFindDevice();
}

void split(const string& src, const string& delim, vector<string>& ret)
{
	size_t last = 0;
	size_t index = src.find_first_of(delim, last);
	while (index != std::string::npos)
	{
		ret.push_back(src.substr(last, index - last));
		last = index + 1;
		index = src.find_first_of(delim, last);
	}
	if (index - last>0)
	{
		ret.push_back(src.substr(last, index - last));
	}
}

bool lessthen (const string& ip1, const string& ip2)
{
	vector<string> ipVec1;
	split(ip1, ".", ipVec1);

	vector<string> ipVec2;
	split(ip2, ".", ipVec2);

	if (ipVec1.size() == 4 && ipVec2.size() == 4)
	{
		for (int nIndex = 0; nIndex < 4; ++nIndex)
		{
			int nVal1 = atoi(ipVec1[nIndex].c_str());
			int nVal2 = atoi(ipVec2[nIndex].c_str());
			if (nVal1 < nVal2)
				return true;
			else if (nVal1 > nVal2)
				return false;
		}
	}
	return false;
};

void CVZLPRClientDemoDlg::OnLPRFindDeviceInfoOutVzlprclientctrlctrl1(LPCTSTR szIPAddr, long usPort1, long usPort2, long SL, long SH)
{
	int nIndex = 0;
		
	CLoginInfo *pLoginInfo = new CLoginInfo();
	pLoginInfo->SetLoginInfo(szIPAddr, usPort1, "admin", "admin", NULL);	

	//根据IP大小进行排序
	//插入到链表的中间部分
	for (int it = 0; it < m_lstFindDevice.GetItemCount(); ++it)
	{
		CLoginInfo *pCompareItem = (CLoginInfo *)m_lstFindDevice.GetItemData(it);		
		if (lessthen(pLoginInfo->GetIP(), pCompareItem->GetIP()))
		{
			break;
		}
		++nIndex;
	}	

	m_lstFindDevice.InsertItem(nIndex, szIPAddr);
	m_lstFindDevice.SetItemState(nIndex, LVNI_FOCUSED | LVIS_SELECTED, LVNI_FOCUSED | LVIS_SELECTED);
	m_lstFindDevice.SetItemData(nIndex, (DWORD_PTR)pLoginInfo);
}

void CVZLPRClientDemoDlg::OnNMClickList1(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMITEMACTIVATE pNMItemActivate = reinterpret_cast<LPNMITEMACTIVATE>(pNMHDR);
	if (pNMItemActivate->iItem < 0)
		return;

	CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstFindDevice.GetItemData(pNMItemActivate->iItem));
	if( pLoginInfo != NULL )
	{
		m_deviceIP.SetWindowText(pLoginInfo->GetIP());
	}

}

void CVZLPRClientDemoDlg::OnBnClickedChkOpen2()
{
	UpdateData(TRUE);

	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );
		int nSizeCur = m_cmbSize.GetCurSel();

		if ( m_bPlateImgOpen )
		{
			m_vzLPRClientCtrl.VzLPRSetIsSavePlateImage(lHandle, TRUE, m_sPlateImgPath.GetBuffer(0) );
			m_oPlateImageEnableMap[lHandle] = true;
		}
		else
		{
			m_vzLPRClientCtrl.VzLPRSetIsSavePlateImage(lHandle, FALSE, "" );
			m_oPlateImageEnableMap[lHandle] = false;
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::deleteDeviceInfo()
{
	for (int i = 0; i < m_lstFindDevice.GetItemCount(); ++i)
	{
		CLoginInfo *pLoginInfo = (CLoginInfo*)(m_lstFindDevice.GetItemData(i));
		if (pLoginInfo)
		{
			delete pLoginInfo;
		}
	}
	m_lstFindDevice.DeleteAllItems();
}

void CVZLPRClientDemoDlg::OnBnClickedBtnStop2()
{
	UpdateData(TRUE);

	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		short nWinCurSel = m_vzLPRClientCtrl.GetCurrentWin();
		int nret = m_vzLPRClientCtrl.VzLPRClientForceTrigger(nWinCurSel);
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}

void CVZLPRClientDemoDlg::OnBnClickedBtnDir2()
{
	TCHAR pszPath[MAX_PATH] = {0};  
	BROWSEINFO bi;   
	bi.hwndOwner      = this->GetSafeHwnd();  
	bi.pidlRoot       = NULL;  
	bi.pszDisplayName = NULL;   
	bi.lpszTitle      = TEXT("请选择文件夹");   
	bi.ulFlags        = BIF_RETURNONLYFSDIRS | BIF_STATUSTEXT;  
	bi.lpfn           = NULL;   
	bi.lParam         = 0;  
	bi.iImage         = 0;   

	LPITEMIDLIST pidl = SHBrowseForFolder(&bi);  
	if (pidl == NULL)  
	{  
		return;  
	}  

	if (SHGetPathFromIDList(pidl, pszPath))  
	{  
		m_sPlateImgPath =   pszPath;
		UpdateData( FALSE );

		string sAppPath = CVzFile::GetAppPath( );
		sAppPath.append("\\config.txt");

		CVzFile::WriteTxtFile( sAppPath.c_str(), pszPath );
	}  

}

void CVZLPRClientDemoDlg::OnBnClickedBtnImg2()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		CExtDlg dlg;
		dlg.SetVzlprclientctrl(&m_vzLPRClientCtrl, lHandle);
		dlg.DoModal( );
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}	
}

void CVZLPRClientDemoDlg::OnBnClickedBtnSaveLastImg()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		CString strIP = m_lstDevice.GetItemText(nCurSel, 0);
		long lHandle = GetItemHandle( nCurSel );

		string sAppPath = CVzFile::GetAppPath( );

		string sFullImgPath = sAppPath;
		sFullImgPath.append("\\");
		sFullImgPath.append(strIP.GetBuffer(0));
		sFullImgPath.append("_plate_img.jpg");

		string sClipImgPath = sAppPath;
		sClipImgPath.append("\\");
		sClipImgPath.append(strIP.GetBuffer(0));
		sClipImgPath.append("_plate_clip_img.jpg");

		short ret  = m_vzLPRClientCtrl.VzLPRSaveLastFullImage(lHandle, sFullImgPath.c_str());
		short ret1 =  m_vzLPRClientCtrl.VzLPRSaveLastClipImage(lHandle, sClipImgPath.c_str());

		if ( ret == 0 && ret1 == 0 )
		{
			MessageBox(_T("保存识别结果图片成功") );
		}
		else
		{
			MessageBox(_T("保存识别结果图片失败，请检查当前是否有识别结果。") );
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}	
	
}

void CVZLPRClientDemoDlg::OnBnClickedBtnStatus()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		int ret = m_vzLPRClientCtrl.VzLPRClientIsConnected(lHandle);
		if ( ret == 1 )
		{
			MessageBox(_T("设备在线! ") );
		}
		else
		{
			MessageBox(_T("设备不在线! ") );
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}	
}

void CVZLPRClientDemoDlg::OnBnClickedBtnSetPwd()
{
	int nCurSel = GetSeleIndex( );
	if( nCurSel >= 0 )
	{
		long lHandle = GetItemHandle( nCurSel );

		CDlgUserPwd dlg;
		dlg.SetVzlprclientctrl(&m_vzLPRClientCtrl, lHandle);
		dlg.DoModal( );
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}
