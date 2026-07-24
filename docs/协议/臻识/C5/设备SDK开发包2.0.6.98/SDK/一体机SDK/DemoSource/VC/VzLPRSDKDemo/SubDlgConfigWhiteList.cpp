// SubDlgConfigWhiteList.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "SubDlgConfigWhiteList.h"

const unsigned g_LPRecPageSize = 100;
const unsigned g_CustomerPageSize = 8;

static void InitVehicle(VZ_LPR_WLIST_VEHICLE* pSrc){
	memset(pSrc,0,sizeof(VZ_LPR_WLIST_VEHICLE));
	pSrc->uCustomerID = -1;
	pSrc->uVehicleID = -1;
}

static void CopyVehicle(const VZ_LPR_WLIST_VEHICLE* pSrc,VZ_LPR_WLIST_VEHICLE* pDest){
	memcpy(pDest, pSrc, sizeof(VZ_LPR_WLIST_VEHICLE));
}

// SubDlgConfigWhiteList dialog

IMPLEMENT_DYNAMIC(SubDlgConfigWhiteList, CDialog)

SubDlgConfigWhiteList::SubDlgConfigWhiteList(CWnd* pParent /*=NULL*/)
	: CDialog(SubDlgConfigWhiteList::IDD, pParent)
	, m_pLPRecords(new VZ_LPR_WLIST_VEHICLE[g_LPRecPageSize]), m_uLPRecNum(0)
	, m_uCurrLPRecPageNum(0)
	, m_pLPCustomers(new VZ_LPR_WLIST_CUSTOMER[g_CustomerPageSize])
	, m_uCustomerNum(0), m_uCurrCustomerPageNum(0),m_lastVehicleListSel(-1),m_lastCustomerListSel(-1)
{
	for(int i=0;i<g_LPRecPageSize;i++){
		InitVehicle(&m_pLPRecords[i]);
	}
	memset(m_pLPCustomers,0,sizeof(VZ_LPR_WLIST_CUSTOMER)*g_CustomerPageSize);

	m_LpSsearchWhere.pSearchConstraints = NULL;
	m_LpSsearchWhere.searchConstraintCount = 0;
	m_LpSsearchWhere.searchType = VZ_LPR_WLIST_SEARCH_TYPE_LIKE;

	m_CusSsearchWhere.pSearchConstraints = NULL;
	m_CusSsearchWhere.searchConstraintCount = 0;
	m_CusSsearchWhere.searchType = VZ_LPR_WLIST_SEARCH_TYPE_LIKE;
}

SubDlgConfigWhiteList::~SubDlgConfigWhiteList()
{
	delete[] m_pLPRecords;
	delete[] m_pLPCustomers;
	if(m_LpSsearchWhere.pSearchConstraints){
		delete m_LpSsearchWhere.pSearchConstraints;
		m_LpSsearchWhere.pSearchConstraints = NULL;
	}
	if(m_CusSsearchWhere.pSearchConstraints){
		delete m_CusSsearchWhere.pSearchConstraints;
		m_CusSsearchWhere.pSearchConstraints = NULL;
	}
}

void SubDlgConfigWhiteList::SetHandle(VzLPRClientHandle handle){
	m_hLPRC = handle;

	m_maxVehicleColorCount = MAX_ENUM_COUNT;
	VzLPRClient_WhiteListGetEnumStrings(m_hLPRC,VZLPRC_WLIST_COL_VEHICLE_COLOR,m_VehicleColorValues,&m_maxVehicleColorCount);
	m_maxPlateTypeCount = MAX_ENUM_COUNT;
	VzLPRClient_WhiteListGetEnumStrings(m_hLPRC,VZLPRC_WLIST_COL_VEHICLE_PLATETYPE,m_PlateTypeValues,&m_maxPlateTypeCount);
}

void SubDlgConfigWhiteList::GetColorValue(int iValue,char* strValue){
	strcpy_s(strValue,VZ_LPR_WLIST_MAX_ENUM_VALUE_LEN,"");
	for(int i=0;i<m_maxVehicleColorCount;i++){
		if(iValue == m_VehicleColorValues[i].iValue){
			strcpy_s(strValue,VZ_LPR_WLIST_MAX_ENUM_VALUE_LEN, m_VehicleColorValues[i].strValue);
			return;
		}
	}
}

void SubDlgConfigWhiteList::GetColorIndex(int &iValue,const char* strValue){	
	for(int i=0;i<m_maxVehicleColorCount;i++){
		if(strcmp(strValue,m_VehicleColorValues[i].strValue)==0){
			iValue = i;
			return;
		}
	}
}

void SubDlgConfigWhiteList::GetPlateTypeValue(int iValue,char* strValue){
	strcpy_s(strValue,VZ_LPR_WLIST_MAX_ENUM_VALUE_LEN,"");
	for(int i=0;i<m_maxPlateTypeCount;i++){
		if(iValue == m_PlateTypeValues[i].iValue){
			strcpy_s(strValue,VZ_LPR_WLIST_MAX_ENUM_VALUE_LEN, m_PlateTypeValues[i].strValue);
			return;
		}
	}
}

void SubDlgConfigWhiteList::GetPlateTypeIndex(int &iValue,const char* strValue){
	for(int i=0;i<m_maxPlateTypeCount;i++){
		if(strcmp(strValue,m_PlateTypeValues[i].strValue)==0){
			iValue = i;
			return;
		}
	}
}

void SubDlgConfigWhiteList::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_LIST_CUSTOMER, m_cListCustomer);
	DDX_Control(pDX, IDC_LIST_LP_REC, m_cListLPRec);
	DDX_Control(pDX, IDC_COMBO1, m_cmbVehicleUpdateOrDeleteBy);
	DDX_Control(pDX, IDC_COMBO2, m_cmbCustomerUpdateOrDeleteBy);
	DDX_Control(pDX, IDC_COMBO3, m_cmbVehicleColor);
	DDX_Control(pDX, IDC_COMBO4, m_cmbPlateType);
}


BEGIN_MESSAGE_MAP(SubDlgConfigWhiteList, CDialog)
	ON_BN_CLICKED(IDC_BTN_SAVE_LP, &SubDlgConfigWhiteList::OnBnClickedBtnSaveLp)
	ON_BN_CLICKED(IDC_BTN_REFRESH_PAGE_LP, &SubDlgConfigWhiteList::OnBnClickedBtnRefreshPageLp)
	ON_BN_CLICKED(IDC_BTN_NEW_LP, &SubDlgConfigWhiteList::OnBnClickedBtnNewLp)
	ON_BN_CLICKED(IDC_BTN_NEW_CUSTOMER, &SubDlgConfigWhiteList::OnBnClickedBtnNewCustomer)
	ON_BN_CLICKED(IDC_BTN_SAVE_CUSTOMER, &SubDlgConfigWhiteList::OnBnClickedBtnSaveCustomer)
	ON_BN_CLICKED(IDC_BTN_REFRESH_PAGE_CUSTOMER, &SubDlgConfigWhiteList::OnBnClickedBtnRefreshPageCustomer)
	ON_BN_CLICKED(IDC_BTN_PREV_PAGE_CUSTOMER, &SubDlgConfigWhiteList::OnBnClickedBtnPrevPageCustomer)
	ON_BN_CLICKED(IDC_BTN_NEXT_PAGE_CUSTOMER, &SubDlgConfigWhiteList::OnBnClickedBtnNextPageCustomer)
	ON_BN_CLICKED(IDC_BTN_DEL_CUSTOMER, &SubDlgConfigWhiteList::OnBnClickedBtnDelCustomer)
	ON_NOTIFY(LVN_ITEMCHANGED, IDC_LIST_CUSTOMER, &SubDlgConfigWhiteList::OnLvnItemchangedListCustomer)
	ON_NOTIFY(LVN_ITEMCHANGED, IDC_LIST_LP_REC, &SubDlgConfigWhiteList::OnLvnItemchangedListLpRec)
	ON_BN_CLICKED(IDC_BTN_SET_CUST_ID, &SubDlgConfigWhiteList::OnBnClickedBtnSetCustId)
	ON_BN_CLICKED(IDC_BTN_PREV_PAGE_LP, &SubDlgConfigWhiteList::OnBnClickedBtnPrevPageLp)
	ON_BN_CLICKED(IDC_BTN_NEXT_PAGE_LP, &SubDlgConfigWhiteList::OnBnClickedBtnNextPageLp)
	ON_BN_CLICKED(IDC_BTN_DEL_LP, &SubDlgConfigWhiteList::OnBnClickedBtnDelLp)
	ON_BN_CLICKED(IDC_BTN_LOOKUP_LP, &SubDlgConfigWhiteList::OnBnClickedBtnLookupLp)
	ON_BN_CLICKED(IDC_BTN_LOOKUP_CUST, &SubDlgConfigWhiteList::OnBnClickedBtnLookupCust)
	ON_EN_CHANGE(IDC_EDIT_LOOKUP_LP, &SubDlgConfigWhiteList::OnEnChangeEditLookupLp)
	ON_EN_CHANGE(IDC_EDIT_LOOKUP_CUST, &SubDlgConfigWhiteList::OnEnChangeEditLookupCust)
	ON_BN_CLICKED(IDC_BTN_LOAD_LPR, &SubDlgConfigWhiteList::OnBnClickedBtnLoadLpr)
	ON_BN_CLICKED(IDC_BTN_LOAD_CUSTOMER, &SubDlgConfigWhiteList::OnBnClickedBtnLoadCustomer)
	ON_BN_CLICKED(IDC_BUTTON1, &SubDlgConfigWhiteList::OnBnClickedButton1)
	ON_BN_CLICKED(IDC_BTN_DEL_BATCH, &SubDlgConfigWhiteList::OnBnClickedBtnDelBatch)
END_MESSAGE_MAP()


void SubDlgConfigWhiteList::iOnQueryLPRecord(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP,
										  const VZ_LPR_WLIST_CUSTOMER *pCustomer)
{
	if(pLP)
	{
		if(m_uLPRecNum == g_LPRecPageSize)
			return;

		char str[64];

		sprintf_s(str, 64, "%d", pLP->uVehicleID);
		m_cListLPRec.InsertItem(m_uLPRecNum,"");

		m_cListLPRec.SetItemText(m_uLPRecNum, 1, str);

		m_cListLPRec.SetItemText(m_uLPRecNum, 2, pLP->strPlateID);

		if( pLP->uCustomerID != VZ_LPR_WLIST_INVAILID_ID){
			sprintf_s(str, 64, "%u", pLP->uCustomerID);
			m_cListLPRec.SetItemText(m_uLPRecNum, 3, str);
		}
		
		sprintf_s(str, 64, "%u", pLP->bEnable);
		m_cListLPRec.SetItemText(m_uLPRecNum, 4, str);

		if(pLP->bEnableTMEnable){
			sprintf_s(str, 64, "%04d-%02d-%02d %02d:%02d:%02d", 
				pLP->struTMEnable.nYear, pLP->struTMEnable.nMonth, pLP->struTMEnable.nMDay,
				pLP->struTMEnable.nHour, pLP->struTMEnable.nMin, pLP->struTMEnable.nSec);
		}
		else{
			str[0] = 0;
		}
		m_cListLPRec.SetItemText(m_uLPRecNum, 5, str);
				
		if(pLP->bEnableTMOverdule){
			sprintf_s(str, 64, "%04d-%02d-%02d %02d:%02d:%02d", 
				pLP->struTMOverdule.nYear, pLP->struTMOverdule.nMonth, pLP->struTMOverdule.nMDay,
				pLP->struTMOverdule.nHour, pLP->struTMOverdule.nMin, pLP->struTMOverdule.nSec);
		}
		else{
			str[0] = 0;
		}
		m_cListLPRec.SetItemText(m_uLPRecNum, 6, str);
		
		sprintf_s(str, 64, "%u", pLP->bUsingTimeSeg);
		m_cListLPRec.SetItemText(m_uLPRecNum, 7, str);

		m_cListLPRec.SetItemText(m_uLPRecNum, 8, "");
		
		sprintf_s(str, 64, "%u", pLP->bAlarm);
		m_cListLPRec.SetItemText(m_uLPRecNum, 9, str);		
		
		m_cListLPRec.SetItemText(m_uLPRecNum, 10, pLP->strCode);

		GetColorValue(pLP->iColor,str);
		m_cListLPRec.SetItemText(m_uLPRecNum, 11, str);		

		GetPlateTypeValue(pLP->iPlateType,str);
		m_cListLPRec.SetItemText(m_uLPRecNum, 12, str);

		m_cListLPRec.SetItemText(m_uLPRecNum, 13, pLP->strComment);

		CopyVehicle(pLP,&m_pLPRecords[m_uLPRecNum]);
		
		m_uLPRecNum++;
	}
	
	if(pCustomer)
	{
		if(m_uCustomerNum == g_CustomerPageSize)
			return;

		char str[32];
		sprintf_s(str, 32, "%u", pCustomer->uCustomerID);
		m_cListCustomer.InsertItem(m_uCustomerNum, str);
		m_cListCustomer.SetItemText(m_uCustomerNum, 1, pCustomer->strName);
		m_cListCustomer.SetItemText(m_uCustomerNum, 2, pCustomer->strCode);
		
		memcpy(m_pLPCustomers + m_uCustomerNum, pCustomer, sizeof(VZ_LPR_WLIST_CUSTOMER));
		m_uCustomerNum++;
	}
}

// SubDlgConfigWhiteList message handlers

BOOL SubDlgConfigWhiteList::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here
	//初始化车牌信息列表
	m_cListLPRec.ModifyStyle(0, LVS_REPORT);
	m_cListLPRec.SetExtendedStyle(m_cListCustomer.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT | LVS_EX_CHECKBOXES);
	m_cListLPRec.InsertColumn(0, "选择",   LVCFMT_CENTER, 40);
	m_cListLPRec.InsertColumn(1, "车辆编号");
	m_cListLPRec.InsertColumn(2, "车牌号");
	m_cListLPRec.InsertColumn(3, "客户编号");
	m_cListLPRec.InsertColumn(4, "启用");
	m_cListLPRec.InsertColumn(5, "生效时间");
	m_cListLPRec.InsertColumn(6, "过期时间");
	m_cListLPRec.InsertColumn(7, "启用时间段");
	m_cListLPRec.InsertColumn(8, "时间段");
	m_cListLPRec.InsertColumn(9, "黑名单");
	m_cListLPRec.InsertColumn(10, "车辆代码");
	m_cListLPRec.InsertColumn(11, "车辆颜色");
	m_cListLPRec.InsertColumn(12, "车牌类型");
	m_cListLPRec.InsertColumn(13, "车牌备注");

	CRect rect; 
	m_cListLPRec.GetClientRect(rect);
	int nUnitW = (rect.Width() - 40)/38;
	m_cListLPRec.SetColumnWidth(1, nUnitW*3);
	m_cListLPRec.SetColumnWidth(2, nUnitW*3);
	m_cListLPRec.SetColumnWidth(3, nUnitW*3);
	m_cListLPRec.SetColumnWidth(4, nUnitW*2);
	m_cListLPRec.SetColumnWidth(5, nUnitW*3);
	m_cListLPRec.SetColumnWidth(6, nUnitW*3);
	m_cListLPRec.SetColumnWidth(7, nUnitW*4);
	m_cListLPRec.SetColumnWidth(8, (int)(nUnitW*2.5));
	m_cListLPRec.SetColumnWidth(9, (int)(nUnitW*2.5));
	m_cListLPRec.SetColumnWidth(10, nUnitW*3);
	m_cListLPRec.SetColumnWidth(11, nUnitW*3);
	m_cListLPRec.SetColumnWidth(12, nUnitW*3);
	m_cListLPRec.SetColumnWidth(13, nUnitW*3);

	//初始化用户列表
	m_cListCustomer.ModifyStyle(0, LVS_REPORT);
	m_cListCustomer.SetExtendedStyle(m_cListCustomer.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);
	m_cListCustomer.InsertColumn(0, "客户编号");
	m_cListCustomer.InsertColumn(1, "姓名");
	m_cListCustomer.InsertColumn(2, "身份码");

	m_cListCustomer.GetClientRect(rect);
	nUnitW = rect.Width()/10;
	m_cListCustomer.SetColumnWidth(0, nUnitW*2);
	m_cListCustomer.SetColumnWidth(1, nUnitW*2);
	m_cListCustomer.SetColumnWidth(2, nUnitW*6);

	m_cmbVehicleUpdateOrDeleteBy.AddString("id");
	m_cmbVehicleUpdateOrDeleteBy.AddString("code");
	m_cmbVehicleUpdateOrDeleteBy.AddString("plateID");

	m_cmbVehicleUpdateOrDeleteBy.SelectString(0,"id");

	m_cmbCustomerUpdateOrDeleteBy.AddString("id");
	m_cmbCustomerUpdateOrDeleteBy.AddString("code");

	m_cmbCustomerUpdateOrDeleteBy.SelectString(0,"id");

	for(int i=0;i<m_maxVehicleColorCount;i++){
		m_cmbVehicleColor.AddString(m_VehicleColorValues[i].strValue);
	}

	for(int i=0;i<m_maxPlateTypeCount;i++){
		m_cmbPlateType.AddString(m_PlateTypeValues[i].strValue);
	}

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}

BOOL SubDlgConfigWhiteList::DestroyWindow()
{
	// TODO: Add your specialized code here and/or call the base class

	return CDialog::DestroyWindow();
}

static int iListCtrlGetCurSel(CListCtrl *pList)
{
	int nCurrSel = -1;
	POSITION pos = pList->GetFirstSelectedItemPosition();
	while(pos)
	{
		nCurrSel = pList->GetNextSelectedItem(pos);
	}

	return(nCurrSel);
}

void SubDlgConfigWhiteList::iQueryLPRecPage()
{
	m_uLPRecNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_LP_REC))->DeleteAllItems();

	VZ_LPR_WLIST_LOAD_CONDITIONS conditions;
	VZ_LPR_WLIST_LIMIT limit;
	limit.limitType = VZ_LPR_WLIST_LIMIT_TYPE_RANGE;
	VZ_LPR_WLIST_RANGE_LIMIT range;
	range.startIndex = m_uCurrLPRecPageNum*g_LPRecPageSize;
	range.count = g_LPRecPageSize;
	limit.pRangeLimit = &range;
	conditions.pLimit = &limit;
	conditions.pSortType = NULL;
	conditions.pSearchWhere = &m_LpSsearchWhere;

	VzLPRClient_WhiteListLoadVehicle(m_hLPRC,
		&conditions);

	if(m_lastVehicleListSel == -1){
		iCleanLPRecEdit();
	}
	else{
		m_cListLPRec.SetItemState(m_lastVehicleListSel, LVIS_SELECTED|LVIS_FOCUSED, LVIS_SELECTED|LVIS_FOCUSED);
	}
}

void SubDlgConfigWhiteList::OnBnClickedBtnSaveLp()
{
	VZ_LPR_WLIST_VEHICLE rec = {0};
	//首先取原来的数据，以防其中有未被设置的数据被清空
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_LP_REC);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel >= 0 && nCurrSel < (int)m_uLPRecNum)
	{
		CopyVehicle(&m_pLPRecords[nCurrSel],&rec);
	}

	rec.bEnable = ((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE))->GetCheck() == BST_CHECKED ? 1 : 0;
	GetDlgItemText(IDC_EDIT_REC_LP, rec.strPlateID, VZ_LPR_WLIST_LP_MAX_LEN);
	GetDlgItemText(IDC_EDIT_VEHICLE_CODE, rec.strCode, VZ_LPR_WLIST_VEHICLE_CODE_LEN);
	GetDlgItemText(IDC_EDIT_VEHICLE_COMMENT, rec.strComment, VZ_LPR_WLIST_VEHICLE_COMMENT_LEN);

	//========更新时间=============
	rec.bEnableTMEnable = TRUE; // (((CButton *)GetDlgItem(IDC_CHK_LP_TIME_ENABLE))->GetCheck() == BST_CHECKED );
	if(rec.bEnableTMEnable){
		CTime cTime;
		((CDateTimeCtrl *)GetDlgItem(IDC_DATE_PICK_ENABLE))->GetTime(cTime);

		rec.struTMEnable.nYear = cTime.GetYear();
		rec.struTMEnable.nMonth = cTime.GetMonth();
		rec.struTMEnable.nMDay = cTime.GetDay();
		((CDateTimeCtrl *)GetDlgItem(IDC_TIME_PICK_ENABLE))->GetTime(cTime);
		rec.struTMEnable.nHour = cTime.GetHour();
		rec.struTMEnable.nMin = cTime.GetMinute();
		rec.struTMEnable.nSec = cTime.GetSecond();
	}
	rec.bEnableTMOverdule = TRUE; // (((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE_OVERDULE))->GetCheck() == BST_CHECKED );
	if(rec.bEnableTMOverdule){
		CTime cTime;
		((CDateTimeCtrl *)GetDlgItem(IDC_DATE_PICK_OVERDULE))->GetTime(cTime);

		rec.struTMOverdule.nYear = cTime.GetYear();
		rec.struTMOverdule.nMonth = cTime.GetMonth();
		rec.struTMOverdule.nMDay = cTime.GetDay();
		((CDateTimeCtrl *)GetDlgItem(IDC_TIME_PICK_OVERDULE))->GetTime(cTime);
		rec.struTMOverdule.nHour = cTime.GetHour();
		rec.struTMOverdule.nMin = cTime.GetMinute();
		rec.struTMOverdule.nSec = cTime.GetSecond();
	}

	rec.bAlarm = ((CButton *)GetDlgItem(IDC_CHK_LP_ALARM))->GetCheck() == BST_CHECKED ? 1 : 0;

	int cursel = m_cmbVehicleColor.GetCurSel();
	if(cursel == -1){
		AfxMessageBox("请选择车辆颜色！");
		return;
	}
	CString cmb_text;
	m_cmbVehicleColor.GetLBText(cursel,cmb_text);

	GetColorIndex(rec.iColor,cmb_text);

	cursel = m_cmbPlateType.GetCurSel();
	if(cursel == -1){
		AfxMessageBox("请选择车辆类型！");
		return;
	}
	m_cmbPlateType.GetLBText(cursel,cmb_text);
	GetPlateTypeIndex(rec.iPlateType,cmb_text);

	char strTemp[32];
	//========检查客户编号============
	GetDlgItemText(IDC_EDIT_REC_LP_CUST_ID, strTemp, 32);
	if(strTemp[0] == 0)
	{
		rec.uCustomerID = -1;
	}
	else{
		rec.uCustomerID = atoi(strTemp);
	}

	//========判断是新增还是更新记录
	GetDlgItemText(IDC_EDIT_REC_ID, strTemp, 32);
	if(strTemp[0] == '-')
	{
		if(VzLPRClient_WhiteListInsertVehicle(m_hLPRC, &rec) < 0)
		{
			MessageBox("记录已存在");
		}
		else
		{
			//成功添加记录，如果本页显示满了，则需要更新下一页，以显示刚添加的记录
			if(m_uLPRecNum == g_CustomerPageSize)
				m_uCurrLPRecPageNum++;
		}
	}
	else
	{
		int ret = -1;
		int cursel = m_cmbVehicleUpdateOrDeleteBy.GetCurSel();
		CString cmb_text;
		m_cmbVehicleUpdateOrDeleteBy.GetLBText(cursel,cmb_text);
		
		if(cmb_text == "id"){
			ret = VzLPRClient_WhiteListUpdateVehicleByID(m_hLPRC, &rec);
		}
		else if(cmb_text == "code"){
			ret = VzLPRClient_WhiteListUpdateVehicleByCode(m_hLPRC, &rec);
		}
		else{
			ret = VzLPRClient_WhiteListUpdateVehicleByPlateID(m_hLPRC, &rec);
		}
		if(ret < 0)
		{
			MessageBox("保存记录失败");
		}
	}

	m_lastVehicleListSel = nCurrSel;

	iQueryLPRecPage();
}

void SubDlgConfigWhiteList::OnBnClickedBtnRefreshPageLp()
{
	iQueryLPRecPage();
}

void SubDlgConfigWhiteList::iCleanLPRecEdit()
{
	//设置新增标识
	SetDlgItemText(IDC_EDIT_REC_ID, "-");
	SetDlgItemText(IDC_EDIT_REC_LP, "");
}

void SubDlgConfigWhiteList::OnBnClickedBtnNewLp()
{
	iCleanLPRecEdit();
	((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE))->SetCheck(BST_CHECKED);
	GetDlgItem(IDC_EDIT_REC_LP)->SetFocus();

	if(m_lastVehicleListSel != -1){
		m_cListLPRec.SetItemState(m_lastVehicleListSel, 0, LVIS_SELECTED|LVIS_FOCUSED);
		m_lastVehicleListSel = -1;
	}
}

void SubDlgConfigWhiteList::iQueryCustomerPage()
{
	m_uCustomerNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER))->DeleteAllItems();

	VZ_LPR_WLIST_LOAD_CONDITIONS conditions;
	VZ_LPR_WLIST_LIMIT limit;
	limit.limitType = VZ_LPR_WLIST_LIMIT_TYPE_RANGE;
	VZ_LPR_WLIST_RANGE_LIMIT range;
	range.startIndex = m_uCurrCustomerPageNum*g_CustomerPageSize;
	range.count = g_CustomerPageSize;
	limit.pRangeLimit = &range;
	conditions.pLimit = &limit;
	conditions.pSortType = NULL;
	conditions.pSearchWhere = &m_CusSsearchWhere;

	VzLPRClient_WhiteListLoadCustomer(m_hLPRC,
		&conditions);

	if(m_lastCustomerListSel == -1){
		iCleanCustomerEdit();
	}
	else{
		m_cListCustomer.SetItemState(m_lastCustomerListSel, LVIS_SELECTED|LVIS_FOCUSED, LVIS_SELECTED|LVIS_FOCUSED);
	}
}

void SubDlgConfigWhiteList::iCleanCustomerEdit()
{
	//设置新增标识
	SetDlgItemText(IDC_EDIT_CUSTOMER_ID, "-");
	SetDlgItemText(IDC_EDIT_CUSTOMER_NAME, "");
	SetDlgItemText(IDC_EDIT_CUSTOMER_CODE, "");
}

void SubDlgConfigWhiteList::OnBnClickedBtnNewCustomer()
{
	iCleanCustomerEdit();
	GetDlgItem(IDC_EDIT_CUSTOMER_NAME)->SetFocus();
	if(m_lastCustomerListSel != -1){
		m_cListCustomer.SetItemState(m_lastCustomerListSel, 0, LVIS_SELECTED|LVIS_FOCUSED);
		m_lastCustomerListSel = -1;
	}
}

void SubDlgConfigWhiteList::OnBnClickedBtnSaveCustomer()
{
	VZ_LPR_WLIST_CUSTOMER cust = {0};
	//首先取原来的数据，以防其中有未被设置的数据被清空
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel >= 0 && nCurrSel < (int)m_uCustomerNum)
	{
		memcpy(&cust, &m_pLPCustomers[nCurrSel], sizeof(VZ_LPR_WLIST_CUSTOMER));
	}

	GetDlgItemText(IDC_EDIT_CUSTOMER_NAME, cust.strName, VZ_LPR_WLIST_CUSTOMER_NAME_LEN);
	GetDlgItemText(IDC_EDIT_CUSTOMER_CODE, cust.strCode, VZ_LPR_WLIST_CUSTOMER_CODE_LEN);

	char strTemp[32];
	//========判断是新增还是更新记录
	GetDlgItemText(IDC_EDIT_CUSTOMER_ID, strTemp, 32);
	if(strTemp[0] == '-')
	{
		if(VzLPRClient_WhiteListInsertCustomer(m_hLPRC, &cust) < 0)
		{
			MessageBox("记录已存在");
		}
		else
		{
			//成功添加记录，如果本页显示满了，则需要更新下一页，以显示刚添加的记录
			if(m_uCustomerNum == g_CustomerPageSize)
				m_uCurrCustomerPageNum++;
		}
	}
	else
	{
		int ret = -1;
		int cursel = m_cmbCustomerUpdateOrDeleteBy.GetCurSel();
		CString cmb_text;
		m_cmbCustomerUpdateOrDeleteBy.GetLBText(cursel,cmb_text);
		
		if(cmb_text == "id"){
			ret = VzLPRClient_WhiteListUpdateCustomerByID(m_hLPRC, &cust);
		}
		else if(cmb_text == "code"){
			ret = VzLPRClient_WhiteListUpdateCustomerByCode(m_hLPRC, &cust);
		}
		if(ret < 0)
		{
			MessageBox("保存记录失败");
		}
	}

	m_lastCustomerListSel = nCurrSel;

	iQueryCustomerPage();//*/
}

void SubDlgConfigWhiteList::OnBnClickedBtnRefreshPageCustomer()
{
	iQueryCustomerPage();
}

void SubDlgConfigWhiteList::OnBnClickedBtnPrevPageCustomer()
{
	if(m_uCurrCustomerPageNum == 0)
		return;

	m_uCurrCustomerPageNum--;

	iQueryCustomerPage();
}

void SubDlgConfigWhiteList::OnBnClickedBtnNextPageCustomer()
{
	if(m_uCustomerNum < g_CustomerPageSize)
		return;
	
	m_uCurrCustomerPageNum++;

	iQueryCustomerPage();
}

void SubDlgConfigWhiteList::OnBnClickedBtnDelCustomer()
{
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel >= (int)m_uCustomerNum)
	{
		return;
	}

	int ret = -1;
	int cursel = m_cmbCustomerUpdateOrDeleteBy.GetCurSel();
	CString cmb_text;
	m_cmbCustomerUpdateOrDeleteBy.GetLBText(cursel,cmb_text);
	
	if(cmb_text == "id"){
		ret = VzLPRClient_WhiteListDeleteCustomerByID(m_hLPRC, m_pLPCustomers[nCurrSel].uCustomerID);
	}
	else if(cmb_text == "code"){
		ret = VzLPRClient_WhiteListDeleteCustomerByCode(m_hLPRC, m_pLPCustomers[nCurrSel].strCode);
	}

	if(m_uCustomerNum == 1)
	{
		if(m_uCurrCustomerPageNum >= 1)
		{
			m_uCurrCustomerPageNum--;
		}
	}

	iQueryCustomerPage();
}

void SubDlgConfigWhiteList::OnLvnItemchangedListCustomer(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMLISTVIEW pNMLV = reinterpret_cast<LPNMLISTVIEW>(pNMHDR);
	// TODO: Add your control notification handler code here
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);

	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel>=(int)m_uCustomerNum){		
		m_lastCustomerListSel = -1;
		return;
	}
	m_lastCustomerListSel = nCurrSel;

	const VZ_LPR_WLIST_CUSTOMER &Cust = m_pLPCustomers[nCurrSel];


	SetDlgItemInt(IDC_EDIT_CUSTOMER_ID, Cust.uCustomerID);

	SetDlgItemText(IDC_EDIT_CUSTOMER_NAME, Cust.strName);
	SetDlgItemText(IDC_EDIT_CUSTOMER_CODE, Cust.strCode);

	//同时更换车牌列表中的ID，以供车牌记录的新建和更新
	SetDlgItemInt(IDC_EDIT_REC_LP_CUST_ID, Cust.uCustomerID);

	*pResult = 0;
}

void SubDlgConfigWhiteList::OnLvnItemchangedListLpRec(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMLISTVIEW pNMLV = reinterpret_cast<LPNMLISTVIEW>(pNMHDR);
	// TODO: Add your control notification handler code here
	
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_LP_REC);
	int nCurrSel = iListCtrlGetCurSel(pList);	
	if(nCurrSel < 0 || nCurrSel >= (int)m_uLPRecNum)
	{
		m_lastVehicleListSel = -1;
		return;
	}
	m_lastVehicleListSel = nCurrSel;

	const VZ_LPR_WLIST_VEHICLE &Rec = m_pLPRecords[nCurrSel];

	SetDlgItemInt(IDC_EDIT_REC_ID, Rec.uVehicleID);
	((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE))->SetCheck(Rec.bEnable ? BST_CHECKED : BST_UNCHECKED);
	SetDlgItemText(IDC_EDIT_REC_LP, Rec.strPlateID);
	SetDlgItemText(IDC_EDIT_VEHICLE_CODE, Rec.strCode);
	SetDlgItemText(IDC_EDIT_VEHICLE_COMMENT, Rec.strComment);
	((CButton *)GetDlgItem(IDC_CHK_LP_TIME_ENABLE))->SetCheck( (Rec.bEnableTMEnable) ? BST_CHECKED : BST_UNCHECKED);
	if(Rec.bEnableTMEnable){
		CTime cTime(Rec.struTMEnable.nYear, Rec.struTMEnable.nMonth, Rec.struTMEnable.nMDay,
			Rec.struTMEnable.nHour, Rec.struTMEnable.nMin, Rec.struTMEnable.nSec);
		((CDateTimeCtrl *)GetDlgItem(IDC_DATE_PICK_ENABLE))->SetTime(&cTime);
		((CDateTimeCtrl *)GetDlgItem(IDC_TIME_PICK_ENABLE))->SetTime(&cTime);
	}
	((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE_OVERDULE))->SetCheck( (Rec.bEnableTMOverdule) ? BST_CHECKED : BST_UNCHECKED);
	if(Rec.bEnableTMOverdule){
		CTime cTime(Rec.struTMOverdule.nYear, Rec.struTMOverdule.nMonth, Rec.struTMOverdule.nMDay,
			Rec.struTMOverdule.nHour, Rec.struTMOverdule.nMin, Rec.struTMOverdule.nSec);
		((CDateTimeCtrl *)GetDlgItem(IDC_DATE_PICK_OVERDULE))->SetTime(&cTime);
		((CDateTimeCtrl *)GetDlgItem(IDC_TIME_PICK_OVERDULE))->SetTime(&cTime);
	}
	((CButton *)GetDlgItem(IDC_CHK_LP_ALARM))->SetCheck(Rec.bAlarm ? BST_CHECKED : BST_UNCHECKED);
	if(Rec.uCustomerID != VZ_LPR_WLIST_INVAILID_ID){
		SetDlgItemInt(IDC_EDIT_REC_LP_CUST_ID, Rec.uCustomerID);
	}
	else{
		SetDlgItemText(IDC_EDIT_REC_LP_CUST_ID, "");
	}
	char str[32];
	GetColorValue(Rec.iColor,str);
	m_cmbVehicleColor.SelectString(-1,str);

	GetPlateTypeValue(Rec.iPlateType,str);
	m_cmbPlateType.SelectString(-1,str);

	*pResult = 0;
}

void SubDlgConfigWhiteList::OnBnClickedBtnSetCustId()
{
	/*CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);

	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel>=(int)m_uCustomerNum)
	{
		MessageBox("请选择一条客户信息记录");
		return;
	}

	const VZ_LPR_WLIST_CUSTOMER &Cust = m_pLPCustomers[nCurrSel];
	SetDlgItemInt(IDC_EDIT_REC_LP_CUST_ID, Cust.uCustomerID);

	pList->SetFocus();*/
}

void SubDlgConfigWhiteList::OnBnClickedBtnPrevPageLp()
{
	if(m_uCurrLPRecPageNum == 0)
		return;

	m_uCurrLPRecPageNum--;

	iQueryLPRecPage();
}

void SubDlgConfigWhiteList::OnBnClickedBtnNextPageLp()
{
	if(m_uLPRecNum < g_LPRecPageSize)
		return;
	
	m_uCurrLPRecPageNum++;

	iQueryLPRecPage();
}

void SubDlgConfigWhiteList::OnBnClickedBtnDelLp()
{
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_LP_REC);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 && nCurrSel >= (int)m_uLPRecNum)
	{
		return;
	}

	int ret = -1;
	int cursel = m_cmbVehicleUpdateOrDeleteBy.GetCurSel();
	CString cmb_text;
	m_cmbVehicleUpdateOrDeleteBy.GetLBText(cursel,cmb_text);
	
	if(cmb_text == "id"){
		ret = VzLPRClient_WhiteListDeleteVehicleByID(m_hLPRC, m_pLPRecords[nCurrSel].uVehicleID);
	}
	else if(cmb_text == "code"){
		ret = VzLPRClient_WhiteListDeleteVehicleByCode(m_hLPRC, m_pLPRecords[nCurrSel].strCode);
	}
	else{
		ret = VzLPRClient_WhiteListDeleteVehicleByPlateID(m_hLPRC, m_pLPRecords[nCurrSel].strPlateID);
	}
	
	if(m_uLPRecNum == 1)
	{
		if(m_uCurrLPRecPageNum >= 1)
		{
			m_uCurrLPRecPageNum--;
		}
	}

	iQueryLPRecPage();
}

void SubDlgConfigWhiteList::OnBnClickedBtnLookupLp()
{
	m_uLPRecNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_LP_REC))->DeleteAllItems();

	iCleanLPRecEdit();

	char str[32];
	GetDlgItemText(IDC_EDIT_LOOKUP_LP, str, 32);

	VzLPRClient_WhiteListLoadVehicleByPlateID(m_hLPRC, str);
}

void SubDlgConfigWhiteList::OnBnClickedBtnLookupCust()
{
	m_uCustomerNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER))->DeleteAllItems();

	iCleanCustomerEdit();

	char str[32];
	GetDlgItemText(IDC_EDIT_LOOKUP_CUST_NAME, str, 32);

	VzLPRClient_WhiteListLoadCustomersByName(m_hLPRC, str);
}

void SubDlgConfigWhiteList::Refresh(){
	iQueryLPRecPage();
	iQueryCustomerPage();
}

void SubDlgConfigWhiteList::OnEnChangeEditLookupLp()
{
	char buffer[256];
	GetDlgItemText(IDC_EDIT_LOOKUP_LP, buffer,256);
	if(strlen(buffer) != 0){
		VZ_LPR_WLIST_SEARCH_CONSTRAINT *pSearch_constraints = new VZ_LPR_WLIST_SEARCH_CONSTRAINT[12];
		m_LpSsearchWhere.pSearchConstraints = pSearch_constraints;
		m_LpSsearchWhere.searchConstraintCount = 12;		
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[0].key, sizeof(m_LpSsearchWhere.pSearchConstraints[0].key), VZLPRC_WLIST_COL_TIME_ON_CREATE);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[1].key, sizeof(m_LpSsearchWhere.pSearchConstraints[1].key), VZLPRC_WLIST_COL_TIME_OVERDUE);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[2].key, sizeof(m_LpSsearchWhere.pSearchConstraints[2].key), VZLPRC_WLIST_COL_ENABLE);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[3].key, sizeof(m_LpSsearchWhere.pSearchConstraints[3].key), VZLPRC_WLIST_COL_LP);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[4].key, sizeof(m_LpSsearchWhere.pSearchConstraints[4].key), VZLPRC_WLIST_COL_TIME_SEG);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[5].key, sizeof(m_LpSsearchWhere.pSearchConstraints[5].key), VZLPRC_WLIST_COL_NEED_ALARM);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[6].key, sizeof(m_LpSsearchWhere.pSearchConstraints[6].key), VZLPRC_WLIST_COL_CUSTOM_ID);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[7].key, sizeof(m_LpSsearchWhere.pSearchConstraints[7].key), VZLPRC_WLIST_COL_VEHICLE_ID);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[8].key, sizeof(m_LpSsearchWhere.pSearchConstraints[8].key), VZLPRC_WLIST_COL_VEHICLE_COLOR);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[9].key, sizeof(m_LpSsearchWhere.pSearchConstraints[9].key), VZLPRC_WLIST_COL_VEHICLE_PLATETYPE);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[10].key, sizeof(m_LpSsearchWhere.pSearchConstraints[10].key), VZLPRC_WLIST_COL_VEHICLE_IDCODE);
		strcpy_s(m_LpSsearchWhere.pSearchConstraints[11].key, sizeof(m_LpSsearchWhere.pSearchConstraints[11].key), VZLPRC_WLIST_COL_VEHICLE_COMMENT);
		for(size_t i=0;i<m_LpSsearchWhere.searchConstraintCount;i++){
			strcpy_s(m_LpSsearchWhere.pSearchConstraints[i].search_string, sizeof(m_LpSsearchWhere.pSearchConstraints[i].search_string), buffer);
		}
	}
	else{
		if(m_LpSsearchWhere.pSearchConstraints){
			delete m_LpSsearchWhere.pSearchConstraints;
			m_LpSsearchWhere.pSearchConstraints = NULL;
		}		
		m_LpSsearchWhere.searchConstraintCount = 0;
	}
	iQueryLPRecPage();
}

void SubDlgConfigWhiteList::OnEnChangeEditLookupCust()
{
	char buffer[256];
	GetDlgItemText(IDC_EDIT_LOOKUP_CUST, buffer,256);
	if(strlen(buffer) != 0){
		VZ_LPR_WLIST_SEARCH_CONSTRAINT* pSsearchConstraints = new VZ_LPR_WLIST_SEARCH_CONSTRAINT[3];
		m_CusSsearchWhere.pSearchConstraints = pSsearchConstraints;
		m_CusSsearchWhere.searchConstraintCount = 3;		
		strcpy_s(m_CusSsearchWhere.pSearchConstraints[0].key, sizeof(m_CusSsearchWhere.pSearchConstraints[0].key), VZLPRC_WLIST_COL_CUSTOM_IDCODE);
		strcpy_s(m_CusSsearchWhere.pSearchConstraints[1].key, sizeof(m_CusSsearchWhere.pSearchConstraints[1].key), VZLPRC_WLIST_COL_CUSTOM_NAME);
		strcpy_s(m_CusSsearchWhere.pSearchConstraints[2].key, sizeof(m_CusSsearchWhere.pSearchConstraints[2].key), VZLPRC_WLIST_COL_CUSTOM_ID);
		for(size_t i=0;i<m_CusSsearchWhere.searchConstraintCount;i++){
			strcpy_s(m_CusSsearchWhere.pSearchConstraints[i].search_string , sizeof(m_CusSsearchWhere.pSearchConstraints[i].search_string), buffer);
		}
	}
	else{
		if(m_CusSsearchWhere.pSearchConstraints){
			delete m_CusSsearchWhere.pSearchConstraints;
			m_CusSsearchWhere.pSearchConstraints = NULL;
		}
		m_CusSsearchWhere.searchConstraintCount = 0;
	}
	iQueryCustomerPage();
}

//测试用
void SubDlgConfigWhiteList::OnBnClickedBtnLoadLpr()
{
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_LP_REC);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 && nCurrSel >= (int)m_uLPRecNum)
	{
		return;
	}

	int ret = -1;
	int cursel = m_cmbVehicleUpdateOrDeleteBy.GetCurSel();
	CString cmb_text;
	m_cmbVehicleUpdateOrDeleteBy.GetLBText(cursel,cmb_text);
	
	if(cmb_text == "id"){
		ret = VzLPRClient_WhiteListLoadVehicleByID(m_hLPRC, m_pLPRecords[nCurrSel].uVehicleID);
	}
	else if(cmb_text == "code"){
		ret = VzLPRClient_WhiteListLoadVehicleByCode(m_hLPRC, m_pLPRecords[nCurrSel].strCode);
	}
	else{
		ret = VzLPRClient_WhiteListLoadVehicleByPlateID(m_hLPRC, m_pLPRecords[nCurrSel].strPlateID);
	}
}
//测试用
void SubDlgConfigWhiteList::OnBnClickedBtnLoadCustomer()
{
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel >= (int)m_uCustomerNum)
	{
		return;
	}

	int ret = -1;
	int cursel = m_cmbCustomerUpdateOrDeleteBy.GetCurSel();
	CString cmb_text;
	m_cmbCustomerUpdateOrDeleteBy.GetLBText(cursel,cmb_text);
	
	if(cmb_text == "id"){
		ret = VzLPRClient_WhiteListLoadCustomerByID(m_hLPRC, m_pLPCustomers[nCurrSel].uCustomerID);
	}
	else if(cmb_text == "code"){
		ret = VzLPRClient_WhiteListLoadCustomerByCode(m_hLPRC, m_pLPCustomers[nCurrSel].strCode);
	}
}

void SubDlgConfigWhiteList::OnBnClickedButton1()
{
	VzLPRClient_WhiteListClearCustomersAndVehicles(m_hLPRC);
	Refresh();
}

BOOL SubDlgConfigWhiteList::PreTranslateMessage(MSG* pMsg)
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

void SubDlgConfigWhiteList::OnBnClickedBtnDelBatch()
{
	for (int i = 0; i < m_cListLPRec.GetItemCount(); ++i)
	{
		if (!m_cListLPRec.GetCheck(i))
			continue;

		CString strPlateID = m_cListLPRec.GetItemText(i, 1);
		int plate_id = atoi(strPlateID.GetBuffer(0));

		int ret = VzLPRClient_WhiteListDeleteVehicleByID(m_hLPRC, plate_id);	
	}

	iQueryLPRecPage();
}
