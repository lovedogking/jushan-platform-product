// OCXGroupDemoDlg.cpp : 实现文件
//

#include "stdafx.h"
#include "OCXGroupDemo.h"
#include "OCXGroupDemoDlg.h"
#include <io.h>
#include <atlimage.h>


#ifdef _DEBUG
#define new DEBUG_NEW
#endif


//*********************************************************
// CLoginInfo类
CLoginInfo::CLoginInfo()
{
	memset(m_szIP, 0, sizeof(m_szIP) );
	m_nPort	= 0;
	memset(m_szUserName, 0, sizeof(m_szUserName) );
	memset(m_szPwd, 0, sizeof(m_szPwd) );
	m_lHandle = 0;
}

CLoginInfo::~CLoginInfo( )
{

}

void CLoginInfo::SetLoginInfo(LPCTSTR strIP, int nPort, LPCTSTR strUserName, LPCTSTR strPwd, long lHandle, int nIsEntryExit )
{
	strcpy_s(m_szIP, sizeof(m_szIP), strIP );
	m_nPort = nPort;
	strcpy_s(m_szUserName, sizeof(m_szUserName), strUserName );
	strcpy_s(m_szPwd, sizeof(m_szPwd), strPwd );
	m_lHandle = lHandle;
	m_nIsEntryExit = nIsEntryExit;
}

long CLoginInfo::GetHandle()
{
	return m_lHandle;
}

int CLoginInfo::GetIsEntryExit()
{
	return m_nIsEntryExit;
}
// 用于应用程序“关于”菜单项的 CAboutDlg 对话框

class CAboutDlg : public CDialog
{
public:
	CAboutDlg();

// 对话框数据
	enum { IDD = IDD_ABOUTBOX };

	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV 支持

// 实现
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


// COCXGroupDemoDlg 对话框




COCXGroupDemoDlg::COCXGroupDemoDlg(CWnd* pParent /*=NULL*/)
	: CDialog(COCXGroupDemoDlg::IDD, pParent)
	, m_strDeviceIP(_T(""))
	, m_strPort(_T(""))
	, m_strUserName(_T(""))
	, m_strPwd(_T(""))
	, m_strDeviceIP2(_T(""))
	, m_strPort2(_T(""))
	, m_strUserName2(_T(""))
	, m_strPwd2(_T(""))
	
{
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
}

void COCXGroupDemoDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_VZLPRCLIENTCTRLCTRL1, m_vzLprCtrl);
	DDX_Control(pDX, IDC_VZLPRCLIENTCTRLCTRL2, m_vzLprVideoCtrl);

	DDX_Text(pDX, IDC_EDIT_DEVICEIP, m_strDeviceIP);
	DDX_Text(pDX, IDC_EDIT_PORT, m_strPort);
	DDX_Text(pDX, IDC_EDIT_USERNAME, m_strUserName);
	DDX_Text(pDX, IDC_EDIT_PWD, m_strPwd);

	DDX_Text(pDX, IDC_EDIT_DEVICEIP2, m_strDeviceIP2);
	DDX_Text(pDX, IDC_EDIT_PORT2, m_strPort2);
	DDX_Text(pDX, IDC_EDIT_USERNAME2, m_strUserName2);
	DDX_Text(pDX, IDC_EDIT_PWD2, m_strPwd2);

	DDX_Control(pDX, IDC_LIST_DEVICE, m_lstDevice);
	DDX_Control(pDX, IDC_LIST_GROUP, m_lstGroupInfo);
	DDX_Control(pDX, IDC_STATIC_ENTRYPLATE, m_sEntryPlate);
	DDX_Control(pDX, IDC_STATIC_EXITPLATE, m_sExitPlate);
}

BEGIN_MESSAGE_MAP(COCXGroupDemoDlg, CDialog)
	ON_WM_SYSCOMMAND()
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	//}}AFX_MSG_MAP
	
	ON_BN_CLICKED(IDC_BTN_OPEN, &COCXGroupDemoDlg::OnBnClickedBtnOpen)
	ON_BN_CLICKED(IDC_BTN_CLOSE, &COCXGroupDemoDlg::OnBnClickedBtnClose)
	ON_BN_CLICKED(IDC_BTN_VIDEOPLAY, &COCXGroupDemoDlg::OnBnClickedBtnVideoplay)
	ON_BN_CLICKED(IDC_BTN_VIDEOSTOP, &COCXGroupDemoDlg::OnBnClickedBtnVideostop)
	ON_BN_CLICKED(IDC_BTN_MANUAL, &COCXGroupDemoDlg::OnBnClickedBtnManual)
	ON_BN_CLICKED(IDC_BTN_OPEN2, &COCXGroupDemoDlg::OnBnClickedBtnOpen2)
	ON_BN_CLICKED(IDC_BTN_CLOSE2, &COCXGroupDemoDlg::OnBnClickedBtnClose2)
	ON_BN_CLICKED(IDC_BTN_GROUPOPEN, &COCXGroupDemoDlg::OnBnClickedBtnGroupopen)
	ON_BN_CLICKED(IDC_BTN_GROUPCLOSE, &COCXGroupDemoDlg::OnBnClickedBtnGroupclose)
	ON_MESSAGE(WM_GROUP_ENTRY_MESSAGE, &COCXGroupDemoDlg::OnShowGroupEntryMessage) 
	ON_MESSAGE(WM_GROUP_EXIT_MESSAGE, &COCXGroupDemoDlg::OnShowGroupExitMessage) 
	ON_MESSAGE(WM_GROUP_ENTRY_IMG_MSG, &COCXGroupDemoDlg::OnShowGroupEntryImgMsg)
	ON_MESSAGE(WM_GROUP_EXIT_IMG_MSG, &COCXGroupDemoDlg::OnShowGroupExitImgMsg)

END_MESSAGE_MAP()


// COCXGroupDemoDlg 消息处理程序

BOOL COCXGroupDemoDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// 将“关于...”菜单项添加到系统菜单中。

	// IDM_ABOUTBOX 必须在系统命令范围内。
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

	// 设置此对话框的图标。当应用程序主窗口不是对话框时，框架将自动
	//  执行此操作
	SetIcon(m_hIcon, TRUE);			// 设置大图标
	SetIcon(m_hIcon, FALSE);		// 设置小图标

	// TODO: 在此添加额外的初始化代码
	m_vzLprCtrl.SetWindowStyle(1);
	m_vzLprVideoCtrl.SetWindowStyle(1);

	m_lstDevice.InsertColumn( 0, "设备IP",   LVCFMT_LEFT, 90 );
	m_lstDevice.InsertColumn( 1, "出入口",   LVCFMT_LEFT, 50 );
	m_lstDevice.InsertColumn( 2, "是否接受组网结果",   LVCFMT_LEFT, 110 );

	m_lstGroupInfo.InsertColumn( 0, "设备IP",    LVCFMT_LEFT, 110 );
	m_lstGroupInfo.InsertColumn( 1, "设备名称",  LVCFMT_LEFT, 100 );
	m_lstGroupInfo.InsertColumn( 2, "车牌号",    LVCFMT_LEFT, 100 );
	m_lstGroupInfo.InsertColumn( 3, "入口时间",  LVCFMT_LEFT, 160 );
	m_lstGroupInfo.InsertColumn( 4, "出口时间",  LVCFMT_LEFT, 160 );

	m_strDeviceIP = _T("192.168.4.97");
	m_strPort     = _T("80");
	m_strUserName = _T("admin");
	m_strPwd      = _T("admin");

	m_strDeviceIP2 = _T("192.168.4.79");
	m_strPort2     = _T("80");
	m_strUserName2 = _T("admin");
	m_strPwd2      = _T("admin");
    UpdateData(FALSE);

	return TRUE;  // 除非将焦点设置到控件，否则返回 TRUE
}

void COCXGroupDemoDlg::OnSysCommand(UINT nID, LPARAM lParam)
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

// 如果向对话框添加最小化按钮，则需要下面的代码
//  来绘制该图标。对于使用文档/视图模型的 MFC 应用程序，
//  这将由框架自动完成。

void COCXGroupDemoDlg::OnPaint()
{
	if (IsIconic())
	{
		CPaintDC dc(this); // 用于绘制的设备上下文

		SendMessage(WM_ICONERASEBKGND, reinterpret_cast<WPARAM>(dc.GetSafeHdc()), 0);

		// 使图标在工作区矩形中居中
		int cxIcon = GetSystemMetrics(SM_CXICON);
		int cyIcon = GetSystemMetrics(SM_CYICON);
		CRect rect;
		GetClientRect(&rect);
		int x = (rect.Width() - cxIcon + 1) / 2;
		int y = (rect.Height() - cyIcon + 1) / 2;

		// 绘制图标
		dc.DrawIcon(x, y, m_hIcon);
	}
	else
	{
		CDialog::OnPaint();
	}
}

//当用户拖动最小化窗口时系统调用此函数取得光标
//显示。
HCURSOR COCXGroupDemoDlg::OnQueryDragIcon()
{
	return static_cast<HCURSOR>(m_hIcon);
}

BOOL COCXGroupDemoDlg::GetIsOpened(LPCTSTR strIP)
{
	BOOL bOpened = FALSE;

	int nRowCount = this->m_lstDevice.GetItemCount();
	for (int i = 0; i < nRowCount; i++)
	{
		CString strDeviceIP = m_lstDevice.GetItemText(i, 0);
		if (strDeviceIP == strIP)
		{
			bOpened = TRUE;
			break;
		}
	}
	return bOpened;
}

int COCXGroupDemoDlg::GetSeleIndex()
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

int COCXGroupDemoDlg::GetSeleIsEntryExit(int nIndex )
{
	int nIsEntryExit = 0;

	if (nIndex >= 0)
	{
		CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nIndex));
		if(pLoginInfo != NULL)
		{
			nIsEntryExit = pLoginInfo->GetIsEntryExit();
		}
	}
	return nIsEntryExit;
}

long COCXGroupDemoDlg::GetItemHandle( int nIndex )
{
	long lHandle = 0;

	if (nIndex >= 0)
	{
		CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nIndex));
		if(pLoginInfo != NULL)
		{
			lHandle = pLoginInfo->GetHandle();
		}
	}

	return lHandle;
}

void COCXGroupDemoDlg::OnBnClickedBtnOpen()
{
	UpdateData(TRUE);

	if (m_strDeviceIP == "")
	{
		MessageBox(_T("请输入设备IP"), _T("提示"), MB_OK);
		return;
	}

	BOOL bOpened = GetIsOpened(m_strDeviceIP.GetBuffer(0));
	if (bOpened)
	{
		CString strMsg;
		strMsg.Format(_T("%s 设备已打开!"), m_strDeviceIP);

		MessageBox(strMsg , _T("提示"), MB_OK);
		return;
	}

	int nPort = atoi(m_strPort.GetBuffer(0));

	long lHandle = m_vzLprCtrl.VzLPRClientOpen(m_strDeviceIP.GetBuffer(0), nPort, m_strUserName.GetBuffer(0), m_strPwd.GetBuffer(0));

	if (lHandle > 0)
	{
		int nIndex = m_lstDevice.InsertItem(0, m_strDeviceIP.GetBuffer(0));
		m_lstDevice.SetItemText(nIndex, 1, "入口");
		m_lstDevice.SetItemText(nIndex, 2, "否");
		m_lstDevice.SetItemState(nIndex, LVNI_FOCUSED |LVNI_SELECTED,LVNI_FOCUSED |LVNI_SELECTED);

		CLoginInfo *pLoginInfo = new CLoginInfo();
		pLoginInfo->SetLoginInfo(m_strDeviceIP.GetBuffer(0),nPort, m_strUserName.GetBuffer(0), m_strPwd.GetBuffer(0), lHandle, 1);

		m_lstDevice.SetItemData(nIndex,(DWORD_PTR) pLoginInfo);

		m_vzLprCtrl.VzLPRSetIsSaveLastImage(lHandle,TRUE, TRUE);
	}
	else
	{
		MessageBox(_T("打开设备失败，请检查设备登录信息"), _T("提示"), MB_OK);
	}
}

void COCXGroupDemoDlg::OnBnClickedBtnClose()
{
	int nCurSel = GetSeleIndex();
	if (nCurSel >= 0)
	{
		long lHandle = GetItemHandle(nCurSel);
		int nIsEntryExit = GetSeleIsEntryExit(nCurSel);//获取选中设备是入口/出口设备
		if (nIsEntryExit == 1)
		{
			int nCurWin = m_vzLprCtrl.GetCurrentWin();
			m_vzLprCtrl.VzLPRClientStopPlay(nCurWin);//停止播放视频

			m_vzLprCtrl.VzLPRClientClose(lHandle);//关闭

			CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nCurSel));
			if (pLoginInfo != NULL)
			{
				delete pLoginInfo;
				pLoginInfo = NULL;
			}

			m_lstDevice.DeleteItem(nCurSel);
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK);
	}
}


void COCXGroupDemoDlg::OnBnClickedBtnVideoplay()
{
	int nCurSel = GetSeleIndex();
	if (nCurSel >= 0)
	{
		long lHandle = GetItemHandle( nCurSel );

		int nIsEntryExit = GetSeleIsEntryExit(nCurSel);//获取选中设备是入口/出口设备
		
		//入口设备视频在第一个窗口播放
		if (nIsEntryExit == 1)
		{
			int nCurWin = m_vzLprCtrl.GetCurrentWin();
			m_vzLprCtrl.VzLPRClientStartPlay(lHandle, nCurWin);
		}
		//出口设备视频在第二个窗口播放
		else if(nIsEntryExit == 2)
		{
			int nCurWin2 = m_vzLprVideoCtrl.GetCurrentWin();
			m_vzLprVideoCtrl.VzLPRClientStartPlay(lHandle, nCurWin2);
		}
	} 
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}


void COCXGroupDemoDlg::OnBnClickedBtnVideostop()
{
	int nCurSel = GetSeleIndex();
	if (nCurSel >= 0)
	{
		long lHandle = GetItemHandle( nCurSel );

		int nIsEntryExit = GetSeleIsEntryExit(nCurSel);

		//停止播放入口视频
		if (nIsEntryExit == 1)
		{
			int nCurWin = m_vzLprCtrl.GetCurrentWin();
			m_vzLprCtrl.VzLPRClientStopPlay(nCurWin);
		}
		//停止播放出口视频
		else if(nIsEntryExit == 2)
		{
			int nCurWin2 = m_vzLprVideoCtrl.GetCurrentWin();
			m_vzLprVideoCtrl.VzLPRClientStopPlay(nCurWin2);
		}
	} 
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );
	}
}


void COCXGroupDemoDlg::OnBnClickedBtnManual()
{
	UpdateData(TRUE);

	int nCurSel = GetSeleIndex();
	if (nCurSel >= 0)
	{
		int nIsEntryExit = GetSeleIsEntryExit(nCurSel);

		if (nIsEntryExit == 1)
		{
			short nWinCurSel = m_vzLprCtrl.GetCurrentWin();
			int nret = m_vzLprCtrl.VzLPRClientForceTrigger(nWinCurSel);
		}
		else if(nIsEntryExit == 2)
		{
			short nWinCurSel2 = m_vzLprVideoCtrl.GetCurrentWin();
			m_vzLprVideoCtrl.VzLPRClientForceTrigger(nWinCurSel2);
		}
	} 
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK );

	}
	
}


CString COCXGroupDemoDlg::GetAppPath()
{
	CString sAppPath;

	char strModuleDir[MAX_PATH ] = {0};
	::GetModuleFileName(NULL, strModuleDir, MAX_PATH);

	sAppPath.Format("%s",strModuleDir);
	int pos = sAppPath.ReverseFind('\\');
	sAppPath = sAppPath.Left(pos);

	return sAppPath;
}

//判断文件是否存在
int COCXGroupDemoDlg::IsPathExist(const char *filename)
{
	return(_access(filename, NULL) == 0);
}

DWORD WINAPI LoadImgProc(LPVOID pParam) 
{
	THREAD_GROUP_PARAM* pGroupParam = (THREAD_GROUP_PARAM*)pParam;

	COCXGroupDemoDlg *pInstance = (COCXGroupDemoDlg *)pGroupParam->userdata	;

	char *img_path = new char[MAX_PATH] ;
	memset(img_path, 0, MAX_PATH);

	CString sAppPath = pInstance->GetAppPath();
	char szImgDir[MAX_PATH] = {0};

	COleDateTime dtNow = COleDateTime::GetCurrentTime();

	sprintf_s(szImgDir, sizeof(szImgDir), "%s\\cap",sAppPath);

	if (!pInstance->IsPathExist(szImgDir))
	{
		CreateDirectory(szImgDir, NULL);
	}

	strcat_s(szImgDir, "\\组网");
	if (!pInstance->IsPathExist(szImgDir))
	{
		CreateDirectory(szImgDir, NULL);
	}

	if (pGroupParam->isEntry)
	{
		strcat_s(szImgDir,"\\出口");
		if (!pInstance->IsPathExist(szImgDir))
		{
			CreateDirectory(szImgDir, NULL);
		}
	}
	else
	{
		strcat_s(szImgDir,"\\入口");
		if (!pInstance->IsPathExist(szImgDir))
		{
			CreateDirectory(szImgDir, NULL);
		}
	}

	sprintf_s(img_path, MAX_PATH,"%s\\%d-%02d-%02d_%02d%02d%02d_%s.jpg",szImgDir, dtNow.GetYear(), dtNow.GetMonth(), dtNow.GetDay(),
		dtNow.GetHour(), dtNow.GetMinute(),dtNow.GetSecond(), pGroupParam->plate);

	if (pGroupParam->isEntry)
	{
		pInstance->m_vzLprVideoCtrl.VzLPRLoadGroupFullImageById(pGroupParam->handle,pGroupParam->id, pGroupParam->sn, img_path);
		pInstance->PostMessage( WM_GROUP_EXIT_IMG_MSG, (WPARAM)img_path, (LPARAM)NULL);
	}
	else
	{	
		pInstance->m_vzLprCtrl.VzLPRLoadGroupFullImageById(pGroupParam->handle,pGroupParam->id, pGroupParam->sn, img_path);
		pInstance->PostMessage(WM_GROUP_ENTRY_IMG_MSG, (WPARAM)img_path, (LPARAM)NULL );
	}

	return (0);
}

void COCXGroupDemoDlg::LoadGroupImg(long handle, long id, LPCTSTR sn,LPCTSTR plate, bool isEntry )
{
	HANDLE hThread;
	DWORD dwTHreadId = 0;

	THREAD_GROUP_PARAM *pGroupThreadParam = NULL;

	pGroupThreadParam = (THREAD_GROUP_PARAM*)malloc(sizeof(THREAD_GROUP_PARAM));
	memset(pGroupThreadParam, 0, sizeof(THREAD_GROUP_PARAM));

	pGroupThreadParam->handle = handle;
	pGroupThreadParam->id = id;
	strcpy_s(pGroupThreadParam->sn, sizeof(pGroupThreadParam->sn) -1, sn);
	strcpy_s(pGroupThreadParam->plate, sizeof(pGroupThreadParam->plate) -1, plate);
	pGroupThreadParam->isEntry = isEntry;
	pGroupThreadParam->userdata = this;

	hThread = CreateThread(NULL, 0, LoadImgProc,(LPVOID)pGroupThreadParam, 0, &dwTHreadId );
	CloseHandle(hThread);
}


LRESULT COCXGroupDemoDlg::OnShowGroupEntryMessage(WPARAM wParam, LPARAM lParam)
{

	LPR_GROUP_INFO *groupResult = (LPR_GROUP_INFO *)lParam;

	if (groupResult != NULL)
	{
		 if(groupResult->recordType == 0)
		{
			int nIndex = m_lstGroupInfo.InsertItem(0, groupResult->entryIP);
			m_lstGroupInfo.SetItemText(nIndex,1, groupResult->entryName);
			m_lstGroupInfo.SetItemText(nIndex,2, groupResult->entryLicense);
			m_lstGroupInfo.SetItemText(nIndex,3, groupResult->entryPlateTime);
		}
	}
	
	//free(groupResult);
	return(0);
}

LRESULT COCXGroupDemoDlg::OnShowGroupExitMessage(WPARAM wParam, LPARAM lParam)
{

	LPR_GROUP_INFO *groupResult = (LPR_GROUP_INFO *)lParam;

	if (groupResult != NULL)
	{
		if (groupResult->recordType == 1)
		{
			int nIndex = m_lstGroupInfo.InsertItem(0, groupResult->exitIP);
			m_lstGroupInfo.SetItemText(nIndex, 1, groupResult->exitName);
			m_lstGroupInfo.SetItemText(nIndex, 2, groupResult->exitLicense);
			m_lstGroupInfo.SetItemText(nIndex, 3, groupResult->entryPlateTime);
			m_lstGroupInfo.SetItemText(nIndex, 4, groupResult->exitPlateTime);
		} 
	}
	
	//free(groupResult);
	return(0);
}


LRESULT COCXGroupDemoDlg::OnShowGroupEntryImgMsg(WPARAM wParam, LPARAM lParam)
{
	char* img_path = (char *)wParam;

	if (img_path != NULL )
	{
		CDC *pDC = m_sEntryPlate.GetDC();

		RECT rc;
		m_sEntryPlate.GetClientRect(&rc);

		CImage img;

		HRESULT hr = img.Load(img_path);

		if (SUCCEEDED(hr))
		{
			RECT rcSrc;
			rcSrc.left = 0;
			rcSrc.top = 0;
			rcSrc.right = img.GetWidth();
			rcSrc.bottom = img.GetHeight();

			SetStretchBltMode(pDC->m_hDC,COLORONCOLOR);

			img.Draw(pDC->m_hDC, rc, rcSrc);
			ReleaseDC(pDC);
		}
		delete[] img_path;
		img_path = NULL;
	}
	return (0);
}


LRESULT COCXGroupDemoDlg::OnShowGroupExitImgMsg(WPARAM wParam, LPARAM lParam)
{

	char* img_path = (char *)wParam;

    if (img_path != NULL )
    {
		CDC *pDC = m_sExitPlate.GetDC();

		RECT rc;
		m_sExitPlate.GetClientRect(&rc);

		CImage img;
		HRESULT hr = img.Load(img_path);
		if (SUCCEEDED(hr))
		{
			RECT rcSrc;
			rcSrc.left = 0;
			rcSrc.top = 0;
			rcSrc.right = img.GetWidth();
			rcSrc.bottom = img.GetHeight();

			SetStretchBltMode(pDC->m_hDC,COLORONCOLOR);

			img.Draw(pDC->m_hDC, rc, rcSrc);
			ReleaseDC(pDC);

		}
		delete[] img_path;
		img_path = NULL;
    }
	return (0);
}


BEGIN_EVENTSINK_MAP(COCXGroupDemoDlg, CDialog)
	ON_EVENT(COCXGroupDemoDlg, IDC_VZLPRCLIENTCTRLCTRL1, 21, COCXGroupDemoDlg::OnLPRPlateGrouptInfoOutVzlprclientctrlctrl1, VTS_BSTR VTS_BSTR VTS_BSTR VTS_I2 VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I4 VTS_BSTR VTS_I2 VTS_BSTR VTS_BSTR VTS_BSTR VTS_I2 VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I4 VTS_BSTR VTS_I2 VTS_I4 VTS_I2)
	ON_EVENT(COCXGroupDemoDlg, IDC_VZLPRCLIENTCTRLCTRL2, 21, COCXGroupDemoDlg::OnLPRPlateGrouptInfoOutVzlprclientctrlctrl2, VTS_BSTR VTS_BSTR VTS_BSTR VTS_I2 VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I4 VTS_BSTR VTS_I2 VTS_BSTR VTS_BSTR VTS_BSTR VTS_I2 VTS_BSTR VTS_BSTR VTS_I2 VTS_I2 VTS_I4 VTS_BSTR VTS_I2 VTS_I4 VTS_I2)
END_EVENTSINK_MAP()

//组网入口设备事件
void COCXGroupDemoDlg::OnLPRPlateGrouptInfoOutVzlprclientctrlctrl1(LPCTSTR exitIP, LPCTSTR exitName, LPCTSTR exitSN, short exitDGType, LPCTSTR exitLicense,
																   LPCTSTR exitColor, short exitPlateType, short exitConfidence,
																   long exitPlateID, LPCTSTR exitPlateTime, short exitResultType,
																   LPCTSTR entryIP, LPCTSTR entryName, LPCTSTR entrySN, short entryDGType,
																   LPCTSTR entryLicense, LPCTSTR entryColor, short entryPlateType, short entryConfidence, long entryPlateID, 
																   LPCTSTR entryPlateTime, short entryResultType, long lprHandle, short recordType)
{

	LPR_GROUP_INFO *groupEntryInfo = (LPR_GROUP_INFO *)malloc(sizeof(LPR_GROUP_INFO));
	memset(groupEntryInfo, 0, sizeof(LPR_GROUP_INFO));

	 if (recordType == 0)
	{
		if (entryIP != "")
		{
			groupEntryInfo->recordType     = 0;
			strcpy_s(groupEntryInfo->entryIP, sizeof(groupEntryInfo->entryIP) -1, entryIP);
			strcpy_s(groupEntryInfo->entryName, sizeof(groupEntryInfo->entryName) -1, entryName);
			strcpy_s(groupEntryInfo->entryLicense, sizeof(groupEntryInfo->entryLicense) -1, entryLicense);
			strcpy_s(groupEntryInfo->entryPlateTime, sizeof(groupEntryInfo->entryPlateTime) -1, entryPlateTime);
			
			PostMessage(WM_GROUP_ENTRY_MESSAGE, (WPARAM)NULL, (LPARAM)groupEntryInfo);
		}

		//获取入口设备图片
		if (lprHandle > 0 && entryPlateID > 0 && entrySN > 0)
		{
			LoadGroupImg(lprHandle, entryPlateID, entrySN, entryLicense, false);
		}
	}
}

void COCXGroupDemoDlg::OnBnClickedBtnOpen2()
{
	UpdateData(TRUE);

	if (m_strDeviceIP2 == "")
	{
		MessageBox(_T("请输入设备IP"), _T("提示"), MB_OK);
		return;
	}

	BOOL bOpened = GetIsOpened(m_strDeviceIP2.GetBuffer(0));
	if (bOpened)
	{
		CString strMsg;
		strMsg.Format(_T("%s 设备已打开!"), m_strDeviceIP2);

		MessageBox(strMsg , _T("提示"), MB_OK);
		return;
	}

	int nPort2 = atoi(m_strPort2.GetBuffer(0));

	long lHandle2 = m_vzLprVideoCtrl.VzLPRClientOpen(m_strDeviceIP2.GetBuffer(0), nPort2, m_strUserName2.GetBuffer(0), m_strPwd2.GetBuffer(0));

	if (lHandle2 > 0)
	{
		int nIndex = m_lstDevice.InsertItem(0, m_strDeviceIP2.GetBuffer(0));
		m_lstDevice.SetItemText( nIndex, 1, "出口");
		m_lstDevice.SetItemText(nIndex, 2, "否");
		m_lstDevice.SetItemState(nIndex, LVNI_FOCUSED |LVNI_SELECTED,LVNI_FOCUSED |LVNI_SELECTED);

		CLoginInfo *pLoginInfo = new CLoginInfo();
		pLoginInfo->SetLoginInfo(m_strDeviceIP2.GetBuffer(0),nPort2, m_strUserName2.GetBuffer(0), m_strPwd2.GetBuffer(0), lHandle2, 2);

		m_lstDevice.SetItemData(nIndex,(DWORD_PTR) pLoginInfo);

		m_vzLprVideoCtrl.VzLPRSetIsSaveLastImage(lHandle2,TRUE, TRUE);
	}
	else
	{
		MessageBox(_T("打开设备失败，请检查设备登录信息"), _T("提示"), MB_OK);
	}
}

void COCXGroupDemoDlg::OnBnClickedBtnClose2()
{
	int nCurSel2 = GetSeleIndex();
	if (nCurSel2 >= 0)
	{
		long lHandle2 = GetItemHandle(nCurSel2);
		int nIsEntryExit = GetSeleIsEntryExit(nCurSel2);//获取选中设备是入口/出口设备
		if (nIsEntryExit == 2)
		{
			int nCurWin2 = m_vzLprVideoCtrl.GetCurrentWin();
			m_vzLprVideoCtrl.VzLPRClientStopPlay(nCurWin2);//关闭视频

			m_vzLprVideoCtrl.VzLPRClientClose(lHandle2);
			CLoginInfo *pLoginInfo = (CLoginInfo *)(m_lstDevice.GetItemData(nCurSel2));
			if (pLoginInfo != NULL)
			{
				delete pLoginInfo;
				pLoginInfo = NULL;
			}
			m_lstDevice.DeleteItem(nCurSel2);
		}
	}
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK);
	}
	
}

void COCXGroupDemoDlg::OnBnClickedBtnGroupopen()
{
	int nCurSel = GetSeleIndex();
	if (nCurSel >= 0)
	{
		long lHandle = GetItemHandle(nCurSel);
		int nIsEntryExit = GetSeleIsEntryExit(nCurSel);
		if (nIsEntryExit == 1)
		{
			int retSetDG1 = m_vzLprCtrl.VzLPRSetDGResultEnable(lHandle, TRUE);
			if (retSetDG1 != 0)
			{
				MessageBox(_T("开启接收组网结果失败!"),_T("提示"), MB_OK);
			}
			else
			{
				m_lstDevice.SetItemText(nCurSel, 2 ,"是");
			}
		} 
		else if (nIsEntryExit == 2)
		{
			int retSetDG2 = m_vzLprVideoCtrl.VzLPRSetDGResultEnable(lHandle, TRUE);
			if (retSetDG2 != 0)
			{
				MessageBox(_T("开启接收组网结果失败!"),_T("提示"), MB_OK);
			}
			else
			{
				m_lstDevice.SetItemText(nCurSel, 2 ,"是");
			}
		}
	} 
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK);
	}
	
}

void COCXGroupDemoDlg::OnBnClickedBtnGroupclose()
{
	int nCurSel = GetSeleIndex();
	if (nCurSel >= 0)
	{
		long lHandle = GetItemHandle(nCurSel);

		int nIsEntryExit = GetSeleIsEntryExit(nCurSel);
		if (nIsEntryExit == 1)
		{
			int retSetDG1 = m_vzLprCtrl.VzLPRSetDGResultEnable(lHandle, FALSE);
			if (retSetDG1 != 0)
			{
				MessageBox(_T("停止接收组网结果失败!"),_T("提示"), MB_OK);
			}
			else
			{
				m_lstDevice.SetItemText(nCurSel, 2 ,"否");
			}
		} 
		else if (nIsEntryExit == 2)
		{
			int retSetDG2 = m_vzLprVideoCtrl.VzLPRSetDGResultEnable(lHandle, FALSE);
			if (retSetDG2 != 0)
			{
				MessageBox(_T("停止接收组网结果失败!"),_T("提示"), MB_OK);
			}
			else
			{
				m_lstDevice.SetItemText(nCurSel, 2 ,"否");
			}
		}
	} 
	else
	{
		MessageBox(_T("请选择设备列表中的一台设备"), _T("提示"), MB_OK);
	}
}

//组网出口设备事件
void COCXGroupDemoDlg::OnLPRPlateGrouptInfoOutVzlprclientctrlctrl2(LPCTSTR exitIP, LPCTSTR exitName, LPCTSTR exitSN, short exitDGType, LPCTSTR exitLicense, LPCTSTR exitColor, short exitPlateType, short exitConfidence, long exitPlateID, 
																   LPCTSTR exitPlateTime, short exitResultType, LPCTSTR entryIP, LPCTSTR entryName, LPCTSTR entrySN, short entryDGType, LPCTSTR entryLicense, LPCTSTR entryColor, short entryPlateType, short entryConfidence, long entryPlateID, 
																   LPCTSTR entryPlateTime, short entryResultType, long lprHandle, short recordType)
{
	LPR_GROUP_INFO *groupInfo = (LPR_GROUP_INFO *)malloc(sizeof(LPR_GROUP_INFO));
	memset(groupInfo,0, sizeof(LPR_GROUP_INFO));

	if (recordType)
	{
		if (exitIP != "")
		{
			groupInfo->recordType     = 1;
			strcpy_s(groupInfo->exitIP, sizeof(groupInfo->exitIP) -1, exitIP);
			strcpy_s(groupInfo->exitName, sizeof(groupInfo->exitName) -1, exitName);
			strcpy_s(groupInfo->exitLicense, sizeof(groupInfo->exitLicense)-1, exitLicense);
			strcpy_s(groupInfo->entryPlateTime, sizeof(groupInfo->entryPlateTime) -1, entryPlateTime);
			strcpy_s(groupInfo->exitPlateTime, sizeof(groupInfo->exitPlateTime) -1, exitPlateTime);

			PostMessage(WM_GROUP_EXIT_MESSAGE, (WPARAM)NULL, (LPARAM)groupInfo);
		}
	} 

	//获取入口设备图片
	if (lprHandle > 0 && exitPlateID > 0 && exitSN > 0)
	{
		LoadGroupImg(lprHandle, exitPlateID, exitSN, exitLicense, true);
	}
}
