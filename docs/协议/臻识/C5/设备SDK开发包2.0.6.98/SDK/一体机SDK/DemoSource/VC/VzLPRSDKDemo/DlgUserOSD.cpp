// DlgUserOSD.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgUserOSD.h"


// CDlgUserOSD dialog

IMPLEMENT_DYNAMIC(CDlgUserOSD, CDialog)

CDlgUserOSD::CDlgUserOSD(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgUserOSD::IDD, pParent)
	, m_strRow1(_T(""))
	, m_strRow2(_T(""))
	, m_strRow3(_T(""))
	, m_strRow4(_T(""))
	, m_strXPos(_T(""))
	, m_strYPos(_T(""))
{
	m_hLPRClient = NULL;
}

CDlgUserOSD::~CDlgUserOSD()
{
}

void CDlgUserOSD::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_EDIT_ROW1, m_strRow1);
	DDX_Text(pDX, IDC_EDIT_ROW2, m_strRow2);
	DDX_Text(pDX, IDC_EDIT_ROW3, m_strRow3);
	DDX_Text(pDX, IDC_EDIT_ROW4, m_strRow4);
	DDX_Control(pDX, IDC_COMBO1, m_cmbColor1);
	DDX_Control(pDX, IDC_COMBO2, m_cmbColor2);
	DDX_Control(pDX, IDC_COMBO3, m_cmbColor3);
	DDX_Control(pDX, IDC_COMBO4, m_cmbColor4);
	DDX_Control(pDX, IDC_COMBO_FONT1, m_cmbFont1);
	DDX_Control(pDX, IDC_COMBO_FONT2, m_cmbFont2);
	DDX_Control(pDX, IDC_COMBO_FONT3, m_cmbFont3);
	DDX_Control(pDX, IDC_COMBO_FONT4, m_cmbFont4);
	DDX_Text(pDX, IDC_EDIT_X_POS, m_strXPos);
	DDX_Text(pDX, IDC_EDIT_Y_POS, m_strYPos);
}


BEGIN_MESSAGE_MAP(CDlgUserOSD, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgUserOSD::OnBnClickedOk)
END_MESSAGE_MAP()


// CDlgUserOSD message handlers

BOOL CDlgUserOSD::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_cmbColor1.AddString("白色");
	m_cmbColor1.AddString("红色");
	m_cmbColor1.AddString("蓝色");
	m_cmbColor1.AddString("绿色");
	m_cmbColor1.SetCurSel(0);

	m_cmbColor2.AddString("白色");
	m_cmbColor2.AddString("红色");
	m_cmbColor2.AddString("蓝色");
	m_cmbColor2.AddString("绿色");
	m_cmbColor2.SetCurSel(0);

	m_cmbColor3.AddString("白色");
	m_cmbColor3.AddString("红色");
	m_cmbColor3.AddString("蓝色");
	m_cmbColor3.AddString("绿色");
	m_cmbColor3.SetCurSel(0);

	m_cmbColor4.AddString("白色");
	m_cmbColor4.AddString("红色");
	m_cmbColor4.AddString("蓝色");
	m_cmbColor4.AddString("绿色");
	m_cmbColor4.SetCurSel(0);

	m_cmbFont1.AddString("16号");
	m_cmbFont1.AddString("24号");
	m_cmbFont1.AddString("32号");
	m_cmbFont1.AddString("48号");
	m_cmbFont1.SetCurSel(0);

	m_cmbFont2.AddString("16号");
	m_cmbFont2.AddString("24号");
	m_cmbFont2.AddString("32号");
	m_cmbFont2.AddString("48号");
	m_cmbFont2.SetCurSel(0);

	m_cmbFont3.AddString("16号");
	m_cmbFont3.AddString("24号");
	m_cmbFont3.AddString("32号");
	m_cmbFont3.AddString("48号");
	m_cmbFont3.SetCurSel(0);

	m_cmbFont4.AddString("16号");
	m_cmbFont4.AddString("24号");
	m_cmbFont4.AddString("32号");
	m_cmbFont4.AddString("48号");
	m_cmbFont4.SetCurSel(0);

	VZ_LPRC_USER_OSD_EX_PARAM osd_param = {0};
	int ret = VzLPRClient_GetUserOsdParamEx(m_hLPRClient, &osd_param);
	if (ret == 0)
	{
		m_cmbColor1.SetCurSel(osd_param.osd_item[0].color);
		m_cmbColor2.SetCurSel(osd_param.osd_item[1].color);
		m_cmbColor3.SetCurSel(osd_param.osd_item[2].color);
		m_cmbColor4.SetCurSel(osd_param.osd_item[3].color);

		m_cmbFont1.SetCurSel(osd_param.osd_item[0].front_size);
		m_cmbFont2.SetCurSel(osd_param.osd_item[1].front_size);
		m_cmbFont3.SetCurSel(osd_param.osd_item[2].front_size);
		m_cmbFont4.SetCurSel(osd_param.osd_item[3].front_size);

		m_strRow1 = osd_param.osd_item[0].text;
		m_strRow2 = osd_param.osd_item[1].text;
		m_strRow3 = osd_param.osd_item[2].text;
		m_strRow4 = osd_param.osd_item[3].text;

		char szXPos[32] = {0};
		sprintf(szXPos, "%d", osd_param.x_pos);
		m_strXPos = szXPos;

		char szYPos[32] = {0};
		sprintf(szYPos, "%d", osd_param.y_pos);
		m_strYPos = szYPos;

		UpdateData(FALSE);
	}

	return TRUE;
}

void CDlgUserOSD::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
	m_hLPRClient = hLPRClient;
}

// 保存OSD配置
void CDlgUserOSD::OnBnClickedOk()
{
	UpdateData(TRUE);
	if (m_strXPos == "" || m_strYPos == "")
	{
		MessageBox("请输入坐标位置");
		return;
	}

	VZ_LPRC_USER_OSD_EX_PARAM osd_param = {0};

	int xPos = atoi(m_strXPos.GetBuffer(0));
	int yPos = atoi(m_strYPos.GetBuffer(0));

	if (xPos < 0 || xPos > 100)
	{
		MessageBox("X坐标位置要求在0-100之间!");
		return;
	}

	if (yPos < 0 || yPos > 100)
	{
		MessageBox("Y坐标位置要求在0-100之间!");
		return;
	}

	osd_param.x_pos = xPos;
	osd_param.y_pos = yPos;

	for(int i = 0; i < MAX_USER_OSD_EX_ITEM_COUNT; i++)
	{
		osd_param.osd_item[i].id = i;
	}

	if(m_strRow1 != "")
	{
		strncpy(osd_param.osd_item[0].text, m_strRow1.GetBuffer(0), sizeof(osd_param.osd_item[0].text)-1);
		osd_param.osd_item[0].display = 1;
		osd_param.osd_item[0].front_size = m_cmbFont1.GetCurSel();
		osd_param.osd_item[0].color = m_cmbColor1.GetCurSel();
	}

	if(m_strRow2 != "")
	{
		strncpy(osd_param.osd_item[1].text, m_strRow2.GetBuffer(0), sizeof(osd_param.osd_item[1].text)-1);
		osd_param.osd_item[1].display = 1;
		osd_param.osd_item[1].front_size = m_cmbFont2.GetCurSel();
		osd_param.osd_item[1].color = m_cmbColor2.GetCurSel();
	}

	if(m_strRow3 != "")
	{
		strncpy(osd_param.osd_item[2].text, m_strRow3.GetBuffer(0), sizeof(osd_param.osd_item[2].text)-1);
		osd_param.osd_item[2].display = 1;
		osd_param.osd_item[2].front_size = m_cmbFont3.GetCurSel();
		osd_param.osd_item[2].color = m_cmbColor3.GetCurSel();
	}

	if(m_strRow3 != "")
	{
		strncpy(osd_param.osd_item[3].text, m_strRow4.GetBuffer(0), sizeof(osd_param.osd_item[3].text)-1);
		osd_param.osd_item[3].display = 1;
		osd_param.osd_item[3].front_size = m_cmbFont4.GetCurSel();
		osd_param.osd_item[3].color = m_cmbColor4.GetCurSel();
	}

	int ret = VzLPRClient_SetUserOsdParamEx(m_hLPRClient, &osd_param);
	if(ret == 0)
	{
		MessageBox("配置自定义OSD成功! ");
	}
	else
	{
		MessageBox("配置自定义OSD失败! ");
	}
}
