// DlgOSDSet.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgOSDSet.h"


//FIXPOINT_SHORT类型为 相对坐标的定点数表示，对于需要输入相对坐标的结构体，可以通过下面的IVS_I2S宏进行转换
/*例如：
窗口分辨率为640*480，坐标点（320，240）
用宏IVS_I2S，我们可以直接得到
VZ_FIXPOINT_SHORT x = IVS_I2S(320，640);
VZ_FIXPOINT_SHORT y = IVS_I2S(240，480);
*/
#define IVS_I2S(int_val,total) ((short)(((int)(int_val)<<14)/((int)(total))))
#define IVS_S2I(short_val,total) (((int)(short_val)*(total)+(1<<13))>>14)

// CDlgOSDSet dialog

IMPLEMENT_DYNAMIC(CDlgOSDSet, CDialog)

CDlgOSDSet::CDlgOSDSet(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgOSDSet::IDD, pParent)
	, m_bShowDate(FALSE)
	, m_bShowTime(FALSE)
	, m_bShowText(FALSE)
	, m_nTimeX(0)
	, m_nDateX(0)
	, m_nDateY(0)
	, m_nTimeY(0)
	, m_nTextX(0)
	, m_nTextY(0)
	, m_strText(_T(""))
	, m_bShowRealtime(FALSE)
	, m_bShowRule(FALSE)
	, m_bShowPos(FALSE)
	, m_bShowDistance(FALSE)
{
	memset( &m_osdParam, 0, sizeof(VZ_LPRC_OSD_Param) );
	memset( &m_showParam, 0, sizeof(VZ_LPRC_REALTIME_SHOW_PARAM) );
	m_hwType = 0;
	m_nBoardVersion = 0;
	m_lprHandle = NULL;
}

CDlgOSDSet::~CDlgOSDSet()
{
}

void CDlgOSDSet::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Check(pDX, IDC_CHK_DATE, m_bShowDate);
	DDX_Check(pDX, IDC_CHK_TIME, m_bShowTime);
	DDX_Check(pDX, IDC_CHK_TEXT, m_bShowText);
	DDX_Control(pDX, IDC_CMB_DATE, m_cmbDate);
	DDX_Control(pDX, IDC_CMB_TIME, m_cmbTime);
	DDX_Text(pDX, IDC_EDIT_TIME_X, m_nTimeX);
	DDV_MinMaxInt(pDX, m_nTimeX, 0, 10000);
	DDX_Text(pDX, IDC_EDIT_DATE_X, m_nDateX);
	DDV_MinMaxInt(pDX, m_nDateX, 0, 10000);
	DDX_Text(pDX, IDC_EDIT_DATE_Y, m_nDateY);
	DDV_MinMaxInt(pDX, m_nDateY, 0, 10000);
	DDX_Text(pDX, IDC_EDIT_TIME_Y, m_nTimeY);
	DDV_MinMaxInt(pDX, m_nTimeY, 0, 10000);
	DDX_Text(pDX, IDC_EDIT_TEXT_X, m_nTextX);
	DDV_MinMaxInt(pDX, m_nTextX, 0, 10000);
	DDX_Text(pDX, IDC_EDIT_TEXT_Y, m_nTextY);
	DDV_MinMaxInt(pDX, m_nTextY, 0, 10000);
	DDX_Text(pDX, IDC_EDIT_TEXT, m_strText);
	DDX_Check(pDX, IDC_CHK_REALTIME, m_bShowRealtime);
	DDX_Check(pDX, IDC_CHK_RULE, m_bShowRule);
	DDX_Check(pDX, IDC_CHK_POS, m_bShowPos);
	DDX_Check(pDX, IDC_CHK_DISTANCE, m_bShowDistance);
}


BEGIN_MESSAGE_MAP(CDlgOSDSet, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgOSDSet::OnBnClickedOk)
END_MESSAGE_MAP()


int get_ivs_i2s(int val1,int val2) {
	int result = (val1 << 14) / val2;
	return result;
}

int get_ivs_s2i(int val1, int val2) {
	int result = (val1 * val2 + (1 << 13)) >> 14;
	return result;
}


// CDlgOSDSet message handlers

BOOL CDlgOSDSet::OnInitDialog()
{
	CDialog::OnInitDialog();

	if( m_nBoardVersion == 8196 )
	{
		// x相机
		GetDlgItem(IDC_CHK_REALTIME)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_CHK_RULE)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_CHK_POS)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_CHK_DISTANCE)->ShowWindow(SW_SHOW);

		VzLPRClient_XGet_OSDParam(m_lprHandle, &m_osdParam, &m_showParam);

		m_bShowRealtime = m_showParam.realtime_result;
		m_bShowRule = m_showParam.virtualloop_area;
		m_bShowPos = m_showParam.plate_pos;
		m_bShowDistance = m_showParam.distance;
	}
	else
	{
		VzLPRClient_GetOsdParam(m_lprHandle, &m_osdParam);
	}

	m_bShowDate = m_osdParam.dstampenable;
	m_bShowTime = m_osdParam.tstampenable;
	m_bShowText = m_osdParam.nTextEnable;

	m_nDateX	= m_osdParam.datePosX;
	m_nDateY	= m_osdParam.datePosY;
	m_nTimeX	= m_osdParam.timePosX;
	m_nTimeY	= m_osdParam.timePosY;
	m_nTextX	= m_osdParam.nTextPositionX;
	m_nTextY	= m_osdParam.nTextPositionY;
	m_strText	= m_osdParam.overlaytext;

	int width = 0;
	if (m_hwType == 2)
	{
		width = 720;
	}
	else if( m_hwType == 3 )
	{
		width = 704;
	}
	else
	{
		width = 704;
	}

	m_nDateX	= get_ivs_s2i(get_ivs_i2s(m_nDateX, width), 100);
	m_nDateY	= get_ivs_s2i(get_ivs_i2s(m_nDateY, 576), 100); 
	m_nTimeX	= get_ivs_s2i(get_ivs_i2s(m_nTimeX, width), 100);
	m_nTimeY	= get_ivs_s2i(get_ivs_i2s(m_nTimeY, 576), 100);
	m_nTextX	= get_ivs_s2i(get_ivs_i2s(m_nTextX, width), 100);
	m_nTextY	= get_ivs_s2i(get_ivs_i2s(m_nTextY, 576), 100);

	GetDlgItem(IDC_STATIC_DATE_X_PER)->ShowWindow(SW_SHOW);
	GetDlgItem(IDC_STATIC_DATE_Y_PER)->ShowWindow(SW_SHOW);
	GetDlgItem(IDC_STATIC_TIME_X_PER)->ShowWindow(SW_SHOW);
	GetDlgItem(IDC_STATIC_TIME_Y_PER)->ShowWindow(SW_SHOW);
	GetDlgItem(IDC_STATIC_TEXT_X_PER)->ShowWindow(SW_SHOW);
	GetDlgItem(IDC_STATIC_TEXT_Y_PER)->ShowWindow(SW_SHOW);

	m_cmbDate.AddString("YYYY/MM/DD");
	m_cmbDate.AddString("MM/DD/YYYY");
	m_cmbDate.AddString("DD/MM/YYYY");

	int nDateFormat = m_osdParam.dateFormat;
	if ( nDateFormat >=0 && nDateFormat < 3 )
	{
		m_cmbDate.SetCurSel(m_osdParam.dateFormat);
	}
	else
	{
		m_cmbDate.SetCurSel(0);
	}

	m_cmbTime.AddString("12Hrs");
	m_cmbTime.AddString("24Hrs");

	int nTimeFormat = m_osdParam.timeFormat;
	if ( nTimeFormat == 0 || nTimeFormat == 1 )
	{
		m_cmbTime.SetCurSel(m_osdParam.timeFormat);
	}
	else
	{
		m_cmbTime.SetCurSel(0);
	}

	UpdateData(FALSE);
	
	return TRUE; 
}

void CDlgOSDSet::SetOSDParam(VZ_LPRC_OSD_Param& osdParam)
{
	memcpy(&m_osdParam, &osdParam, sizeof(VZ_LPRC_OSD_Param) );
}

// 保存OSD配置
void CDlgOSDSet::OnBnClickedOk()
{
	UpdateData(TRUE);

	bool value = false;

	do{
		if( m_nDateX < 0 || m_nDateX > 100 )
		{
			break;
		}

		if( m_nDateY < 0 || m_nDateY > 100 )
		{
			break;
		}

		if( m_nTimeX < 0 || m_nTimeX > 100 )
		{
			break;
		}

		if( m_nTimeY < 0 || m_nTimeY > 100 )
		{
			break;
		}

		if( m_nTextX < 0 || m_nTextX > 100 )
		{
			break;
		}

		if( m_nTextY < 0 || m_nTextY > 100 )
		{
			break;
		}

		value = true;
	}while(0);

	if( !value )
	{
		MessageBox("比例在0-100之间，请重新输入。");
		return;
	}

	m_osdParam.dstampenable = m_bShowDate;
	m_osdParam.tstampenable = m_bShowTime;
	m_osdParam.nTextEnable  = m_bShowText;

	int width = 0;
	if( m_hwType == 1 )
	{
		width = 704;
	}
	else if (m_hwType == 2)
	{
		width = 720;
	}
	else if(m_hwType == 3)
	{
		width = 704;
	}

	m_osdParam.datePosX		= get_ivs_s2i(get_ivs_i2s(m_nDateX, 100), width);
	m_osdParam.datePosY		= get_ivs_s2i(get_ivs_i2s(m_nDateY, 100), 576);
	m_osdParam.timePosX		= get_ivs_s2i(get_ivs_i2s(m_nTimeX, 100), width);
	m_osdParam.timePosY     = get_ivs_s2i(get_ivs_i2s(m_nTimeY, 100), 576);
	m_osdParam.nTextPositionX	= get_ivs_s2i(get_ivs_i2s(m_nTextX, 100), width);
	m_osdParam.nTextPositionY	= get_ivs_s2i(get_ivs_i2s(m_nTextY, 100), 576);
	
	memset( m_osdParam.overlaytext, 0, sizeof(m_osdParam.overlaytext) );
	strncpy( m_osdParam.overlaytext, m_strText.GetBuffer(0), sizeof(m_osdParam.overlaytext) - 1 );

	if(strlen(m_osdParam.overlaytext) > 60)
	{
		MessageBox("文本长度超过60个字符，请重新输入! ");
		return;
	}

	m_osdParam.dateFormat = m_cmbDate.GetCurSel();
	m_osdParam.timeFormat = m_cmbTime.GetCurSel();

	int ret = -1;

	if( m_nBoardVersion == 8196 )
	{
		// x相机
		m_showParam.realtime_result  = m_bShowRealtime; 
		m_showParam.virtualloop_area = m_bShowRule;
		m_showParam.plate_pos		 = m_bShowPos; 
		m_showParam.distance		 = m_bShowDistance;

		ret = VzLPRClient_XSet_OSDParam(m_lprHandle, &m_osdParam, &m_showParam);
	}
	else
	{
		ret = VzLPRClient_SetOsdParam( m_lprHandle, &m_osdParam );	
	}

	if(ret == 0)
	{
		MessageBox("配置OSD参数成功！");
	}
	else
	{
		MessageBox("配置OSD参数失败，请重试！");
	}
	

	OnOK();
}

VZ_LPRC_OSD_Param* CDlgOSDSet::GetOSDParam( )
{
	return &m_osdParam;
}

void CDlgOSDSet::SetHwType( int nType, int board_version )
{
	m_hwType = nType;
	m_nBoardVersion = board_version;
}

void CDlgOSDSet::SetLPRHandle( VzLPRClientHandle lprHandle )
{
	m_lprHandle = lprHandle;
}