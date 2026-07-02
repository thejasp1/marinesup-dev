<?php
include('auth.php'); // 👈 TOEVOEGEN
include('config.php');

$conn = new PDO(
"mysql:host=$servername;dbname=$dbname;charset=utf8mb4",
$username,
$password,
[PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
);

$editMode=false;
$editData=null;

/* SORTERING */

$allowedSort=['id','bootnaam','imo','email','purchasing_email','updated_at'];
$sort=$_GET['sort'] ?? 'id';
$order=$_GET['order'] ?? 'DESC';

if(!in_array($sort,$allowedSort)) $sort='id';
$order=strtoupper($order)==='ASC'?'ASC':'DESC';

/* ZOEKEN */

$search=trim($_GET['search'] ?? '');

$where="";
$params=[];

if($search!==""){

$where="WHERE
s.bootnaam LIKE ?
OR s.imo LIKE ?
OR s.email LIKE ?
OR s.purchasing_email LIKE ?";

$params=[
"%$search%",
"%$search%",
"%$search%",
"%$search%"
];

}

/* CSV IMPORT */

if(isset($_POST['import_csv']) && isset($_FILES['csv_file'])){

if(($handle=fopen($_FILES['csv_file']['tmp_name'],"r"))!==false){

$header=fgetcsv($handle,2000,",");
$cols=array_flip($header);

while(($data=fgetcsv($handle,2000,","))!==false){

$id=$data[$cols['ID']] ?? '';
$bootnaam=strtoupper(trim($data[$cols['BOOTNAAM']] ?? ''));
$imo=trim($data[$cols['IMO']] ?? '');
$email=strtoupper(trim($data[$cols['SHIP EMAIL']] ?? ''));
$purchasing=strtoupper(trim($data[$cols['PURCHASING EMAIL']] ?? ''));

if(!$bootnaam || !$imo) continue;

$stmt=$conn->prepare("
INSERT INTO ship_contacts
(id,bootnaam,imo,email,purchasing_email,updated_at)
VALUES (?,?,?,?,?,NOW())
ON DUPLICATE KEY UPDATE
bootnaam=VALUES(bootnaam),
email=VALUES(email),
purchasing_email=VALUES(purchasing_email),
updated_at=NOW()
");

$stmt->execute([$id,$bootnaam,$imo,$email,$purchasing]);

}

fclose($handle);

}

header("Location: ".$_SERVER['PHP_SELF']);
exit;

}

/* EXPORT */

if(isset($_GET['export'])){

header('Content-Type:text/csv');
header('Content-Disposition:attachment;filename=ships_export.csv');

$out=fopen("php://output","w");

fputcsv($out,[
'ID',
'BOOTNAAM',
'IMO',
'SHIP EMAIL',
'PURCHASING EMAIL',
'WMS',
'REDERIJ',
'UPDATED'
]);

$stmt=$conn->query("
SELECT 
s.*,
c.default_wms,
c.default_rederij
FROM ship_contacts s
LEFT JOIN customer_preferences c
ON (
UPPER(TRIM(s.purchasing_email))=UPPER(TRIM(c.purchasing_email))
OR
UPPER(TRIM(s.email))=UPPER(TRIM(c.purchasing_email))
)
");

while($row=$stmt->fetch(PDO::FETCH_ASSOC)){

fputcsv($out,[
$row['id'],
$row['bootnaam'],
$row['imo'],
$row['email'],
$row['purchasing_email'],
$row['default_wms']?'AAN':'UIT',
$row['default_rederij']?'AAN':'UIT',
$row['updated_at']
]);

}

exit;

}

/* DELETE */

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['delete'])) {
    $stmt = $conn->prepare("DELETE FROM ship_contacts WHERE id=?");
    $stmt->execute([$_POST['delete']]);
    header("Location: ".$_SERVER['PHP_SELF']);
    exit;
}

/* EDIT */

if(isset($_GET['edit'])){

$stmt=$conn->prepare("SELECT * FROM ship_contacts WHERE id=?");
$stmt->execute([$_GET['edit']]);

$editData=$stmt->fetch(PDO::FETCH_ASSOC);
$editMode=true;

}

/* SAVE */

if($_SERVER['REQUEST_METHOD']==='POST' && !isset($_POST['import_csv'])){

$bootnaam=strtoupper(trim($_POST['bootnaam']));
$imo=strtoupper(trim($_POST['imo']));
$email=strtoupper(trim($_POST['email']));
$purchasing=strtoupper(trim($_POST['purchasing_email']));
$send_order_received  = isset($_POST['send_order_received']) ? 1 : 0;
$send_order_processed = isset($_POST['send_order_processed']) ? 1 : 0;
$send_shipment_final  = isset($_POST['send_shipment_final']) ? 1 : 0;
$send_delivery        = isset($_POST['send_delivery']) ? 1 : 0;
$send_complaint       = isset($_POST['send_complaint']) ? 1 : 0;

if(!empty($_POST['id'])){

$stmt=$conn->prepare("
UPDATE ship_contacts
SET
bootnaam=?,
imo=?,
email=?,
purchasing_email=?,
send_order_received=?,
send_order_processed=?,
send_shipment_final=?,
send_delivery=?,
send_complaint=?,
updated_at=NOW()
WHERE id=?
");

$stmt->execute([
$bootnaam,
$imo,
$email,
$purchasing,
$send_order_received,
$send_order_processed,
$send_shipment_final,
$send_delivery,
$send_complaint,
$_POST['id']
]);

}else{

$stmt=$conn->prepare("
INSERT INTO ship_contacts
(
bootnaam,
imo,
email,
purchasing_email,
send_order_received,
send_order_processed,
send_shipment_final,
send_delivery,
send_complaint,
updated_at
)
VALUES (?,?,?,?,?,?,?,?,?,NOW())
");

$stmt->execute([
$bootnaam,
$imo,
$email,
$purchasing,
$send_order_received,
$send_order_processed,
$send_shipment_final,
$send_delivery,
$send_complaint
]);
}

header("Location: ".$_SERVER['PHP_SELF']);
exit;

}

/* DATA */

$stmt=$conn->prepare("
SELECT 
s.*,
c.default_wms,
c.default_rederij
FROM ship_contacts s
LEFT JOIN customer_preferences c
ON (
UPPER(TRIM(s.purchasing_email))=UPPER(TRIM(c.purchasing_email))
OR
UPPER(TRIM(s.email))=UPPER(TRIM(c.purchasing_email))
)
$where
ORDER BY $sort $order
");

$stmt->execute($params);
$records=$stmt->fetchAll(PDO::FETCH_ASSOC);

/* STATS */

$total=$conn->query("SELECT COUNT(*) FROM ship_contacts")->fetchColumn();

$noEmail=$conn->query("
SELECT COUNT(*) 
FROM ship_contacts
WHERE email='' OR email IS NULL
")->fetchColumn();

?>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Ship Contacts Dashboard</title>

<style>

body{font-family:Segoe UI;background:#f4f6f9;margin:0;}

.header{
background:#1f2937;
color:white;
padding:15px 30px;
display:flex;
justify-content:space-between;
align-items:center;
}

.header a{
color:white;
text-decoration:none;
margin-left:15px;
font-size:13px;
}

.container{padding:25px;}

.stats{display:flex;gap:20px;margin-bottom:20px;}

.stat-box{
background:white;
padding:15px;
border-radius:8px;
box-shadow:0 4px 12px rgba(0,0,0,0.05);
flex:1;
text-align:center;
}

.card{
background:white;
padding:20px;
border-radius:8px;
box-shadow:0 4px 12px rgba(0,0,0,0.05);
margin-bottom:20px;
}

input{
width:100%;
padding:6px;
margin-bottom:10px;
border:1px solid #ccc;
border-radius:6px;
font-size:13px;
}

button{
background:#2563eb;
color:white;
border:none;
padding:7px 14px;
border-radius:6px;
cursor:pointer;
font-size:13px;
}

table{width:100%;border-collapse:collapse;font-size:13px;}

th{
padding:8px;
border-bottom:1px solid #eee;
text-align:center;
vertical-align:middle;
}

td{
padding:8px;
border-bottom:1px solid #eee;
vertical-align:middle;
}

table td:nth-child(1),
table td:nth-child(3),
table td:nth-child(6),
table td:nth-child(7),
table td:nth-child(8),
table td:nth-child(9),
table td:nth-child(10),
table td:nth-child(11),
table td:nth-child(12),
table td:nth-child(13),
table td:nth-child(14){
    text-align:center;
}

.actions a{
padding:4px 8px;
font-size:12px;
border-radius:4px;
text-decoration:none;
}

.actions{
    white-space: nowrap;
}

.actions form{
    display:inline;
    margin:0;
    padding:0;
}

.actions button{
    display:inline-block;
    margin-left:4px;
}

.edit{
    display:inline-block;
    background:#facc15;
    color:#000;
    padding:5px 10px;
    border-radius:4px;
    text-decoration:none;
    font-size:12px;
}

.delete{
    display:inline-block;
    background:#ef4444;
    color:#fff;
    border:none;
    padding:5px 10px;
    border-radius:4px;
    cursor:pointer;
    font-size:12px;
}
.document-options{
    display:flex;
    flex-wrap:wrap;
    gap:20px;
    align-items:center;
    margin:15px 0;
}

.document-options label{
    display:flex;
    align-items:center;
    gap:6px;
    margin:0;
    font-size:13px;
    white-space:nowrap;
}

.document-options input[type="checkbox"]{
    width:auto;
    margin:0;
}
</style>
</head>

<body>

<div class="header">

<div>Ship Contacts Dashboard</div>

<div>
<a href="#" onclick="document.getElementById('csvInput').click()">📄 Import CSV</a>
<a href="?export=1">📥 Export CSV</a>
<a href="index.html">🏠 Home</a>
</div>

</div>

<div class="container">

<form method="post" enctype="multipart/form-data" id="importForm" style="display:none;">
<input type="file" id="csvInput" name="csv_file" required>
<input type="hidden" name="import_csv" value="1">
</form>

<div class="stats">

<div class="stat-box">
<strong><?= $total ?></strong><br>Schepen
</div>

<div class="stat-box">
<strong><?= $noEmail ?></strong><br>Geen Ship Email
</div>

</div>

<div class="card">

<form method="get">
<input type="text" name="search" placeholder="Zoek schip..."
value="<?= htmlspecialchars($search) ?>">
<button type="submit">Zoeken</button>
</form>

</div>

<div class="card">

<h3><?= $editMode?"Bewerken":"Nieuwe Ship Contact" ?></h3>

<form method="post">

<input type="hidden" name="id"
value="<?= htmlspecialchars($editData['id'] ?? '') ?>">

<input type="text" name="bootnaam" placeholder="BOOTNAAM"
value="<?= htmlspecialchars($editData['bootnaam'] ?? '') ?>">

<input type="text" name="imo" placeholder="IMO"
value="<?= htmlspecialchars($editData['imo'] ?? '') ?>">

<input type="text" name="email" placeholder="SHIP EMAIL"
value="<?= htmlspecialchars($editData['email'] ?? '') ?>">

<input type="text" name="purchasing_email" placeholder="PURCHASING EMAIL"
value="<?= htmlspecialchars($editData['purchasing_email'] ?? '') ?>">

<hr>

<h3>Documenten versturen</h3>

<div class="document-options">

<label>
    <input type="checkbox" name="send_order_received" value="1"
    <?= ($editData['send_order_received'] ?? 1) ? 'checked' : '' ?>>
    📥 Order(s) Ontvangen
</label>

<label>
    <input type="checkbox" name="send_order_processed" value="1"
    <?= ($editData['send_order_processed'] ?? 1) ? 'checked' : '' ?>>
    ⚙️ Order(s) Verwerkt
</label>

<label>
    <input type="checkbox" name="send_shipment_final" value="1"
    <?= ($editData['send_shipment_final'] ?? 1) ? 'checked' : '' ?>>
    📦 Zending Definitief
</label>

<label>
    <input type="checkbox" name="send_delivery" value="1"
    <?= ($editData['send_delivery'] ?? 1) ? 'checked' : '' ?>>
    🚚 Zending Afgeleverd
</label>

<label>
    <input type="checkbox" name="send_complaint" value="1"
    <?= ($editData['send_complaint'] ?? 1) ? 'checked' : '' ?>>
    ⚠️ Reclamatie
</label>

</div>

<button type="submit"><?= $editMode ? "Bijwerken" : "Opslaan" ?></button>
</form>

</div>

<table>

<tr>
<th>ID</th>
<th>BOOTNAAM</th>
<th>IMO</th>
<th>SHIP EMAIL</th>
<th>REDERIJ EMAIL</th>
<th style="text-align:center;" title="WMS">WMS</th>

<th style="text-align:center;" title="Rederij">REDERIJ</th>

<th style="text-align:center;" title="Order(s) Ontvangen">📥</th>

<th style="text-align:center;" title="Order(s) Verwerkt">⚙️</th>

<th style="text-align:center;" title="Zending Definitief">📦</th>

<th style="text-align:center;" title="Zending Afgeleverd">🚚</th>

<th style="text-align:center;" title="Reclamatie">⚠️</th><th>UPDATED</th>
<th>ACTIES</th>
</tr>

<?php foreach($records as $r): ?>

<tr>

<td><?= str_pad($r['id'],6,'0',STR_PAD_LEFT) ?></td>
<td><a href="ship_details.php?imo=<?= urlencode($r['imo']) ?>" style="color: #2563eb; font-weight: bold; text-decoration: none;"><?= htmlspecialchars($r['bootnaam']) ?></a></td>
<td><?= htmlspecialchars($r['imo']) ?></td>
<td><?= htmlspecialchars($r['email']) ?></td>
<td><?= htmlspecialchars($r['purchasing_email']) ?></td>
<td style="text-align:center;">
<?= ($r['default_wms'] ?? 0) ? '✅' : '❌' ?>
</td>

<td style="text-align:center;">
<?= ($r['default_rederij'] ?? 0) ? '✅' : '❌' ?>
</td>

<td style="text-align:center;">
<?= $r['send_order_received'] ? '✅' : '❌' ?>
</td>

<td style="text-align:center;">
<?= $r['send_order_processed'] ? '✅' : '❌' ?>
</td>

<td style="text-align:center;">
<?= $r['send_shipment_final'] ? '✅' : '❌' ?>
</td>

<td style="text-align:center;">
<?= $r['send_delivery'] ? '✅' : '❌' ?>
</td>

<td style="text-align:center;">
<?= $r['send_complaint'] ? '✅' : '❌' ?>
</td>

<td><?= htmlspecialchars($r['updated_at']) ?></td>

<td class="actions">

    <a class="edit" href="?edit=<?= $r['id'] ?>">
        ✏️ Bewerken
    </a>

    <form method="post" style="display:inline;">
        <input type="hidden" name="delete" value="<?= $r['id'] ?>">
        <button
            type="submit"
            class="delete"
            onclick="return confirm('Weet je zeker dat je dit schip wilt verwijderen?')">
            🗑️ Verwijderen
        </button>
    </form>

</td>
</tr>

<?php endforeach; ?>

</table>

</div>

<script>

document.getElementById("csvInput").addEventListener("change",function(){
document.getElementById("importForm").submit();
});

</script>

</body>
</html>