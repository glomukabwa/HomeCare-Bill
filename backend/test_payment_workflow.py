"""Isolated payment workflow tests: no live Firebase writes or Daraja calls.
Run from backend: python -m unittest -v test_payment_workflow.py
"""
import copy
import unittest
from unittest.mock import patch

import payment_routes as routes
import mpesa_service


class FakeSnapshot:
    def __init__(self, data):
        self._data = copy.deepcopy(data)
        self.exists = data is not None

    def to_dict(self):
        return copy.deepcopy(self._data)

    def get(self, key):
        return self._data.get(key) if self._data is not None else None


class FakeDocument:
    def __init__(self, store, collection, document_id):
        self.store = store
        self.collection = collection
        self.id = document_id

    def get(self, transaction=None):
        return FakeSnapshot(self.store.data.get(self.collection, {}).get(self.id))


class FakeCollection:
    def __init__(self, store, name):
        self.store = store
        self.name = name

    def document(self, document_id):
        return FakeDocument(self.store, self.name, document_id)


class FakeTransaction:
    def __init__(self, store):
        self.store = store
        self.write_count = 0

    def update(self, ref, updates):
        self.store.data[ref.collection][ref.id].update(updates)
        self.write_count += 1
        self.store.total_writes += 1

    def create(self, ref, values):
        target = self.store.data.setdefault(ref.collection, {})
        if ref.id in target:
            raise AssertionError('Document already exists')
        target[ref.id] = dict(values)
        self.write_count += 1
        self.store.total_writes += 1


class FakeDB:
    def __init__(self, invoice_status='APPROVED'):
        self.data = {
            'invoices': {'inv-1': {
                'clientId': 'client-1',
                'status': invoice_status,
                'paymentState': 'PENDING',
                'currentPaymentId': 'pay-1',
                'totalCents': 150000,
                'caregiverId': 'caregiver-1',
            }},
            'payments': {'pay-1': {
                'invoiceId': 'inv-1',
                'clientId': 'client-1',
                'amountCents': 150000,
                'status': 'PENDING',
                'checkoutRequestId': 'checkout-1',
            }},
        }
        self.total_writes = 0

    def collection(self, name):
        return FakeCollection(self, name)

    def transaction(self):
        return FakeTransaction(self)


def passthrough_transactional(fn):
    return fn


class PaymentWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.db = FakeDB()
        self.patches = [
            patch.object(routes, 'db', self.db),
            patch.object(routes.firestore, 'transactional', passthrough_transactional),
        ]
        for p in self.patches:
            p.start()
            self.addCleanup(p.stop)

    def test_verified_success_updates_invoice_and_payment(self):
        with patch.object(routes, 'query_stk_push_status', return_value={
            'status': 'SUCCESS', 'resultCode': 0, 'description': 'Completed'
        }):
            result = routes.verify_and_finalize('pay-1')
        self.assertEqual(result['status'], 'SUCCESS')
        self.assertEqual(self.db.data['payments']['pay-1']['status'], 'SUCCESS')
        self.assertEqual(self.db.data['invoices']['inv-1']['status'], 'PAID')
        self.assertEqual(self.db.data['invoices']['inv-1']['paymentState'], 'PAID')
        self.assertIn('paidAt', self.db.data['invoices']['inv-1'])

    def test_verified_cancellation_remains_unpaid(self):
        with patch.object(routes, 'query_stk_push_status', return_value={
            'status': 'FAILED', 'resultCode': 1032, 'description': 'Cancelled'
        }):
            result = routes.verify_and_finalize('pay-1')
        self.assertEqual(result['status'], 'FAILED')
        self.assertEqual(self.db.data['payments']['pay-1']['resultCode'], 1032)
        self.assertEqual(self.db.data['invoices']['inv-1']['status'], 'APPROVED')
        self.assertEqual(self.db.data['invoices']['inv-1']['paymentState'], 'FAILED')

    def test_verified_timeout_remains_unpaid(self):
        with patch.object(routes, 'query_stk_push_status', return_value={
            'status': 'FAILED', 'resultCode': 1037, 'description': 'Timed out'
        }):
            result = routes.verify_and_finalize('pay-1')
        self.assertEqual(result['status'], 'FAILED')
        self.assertEqual(self.db.data['invoices']['inv-1']['status'], 'APPROVED')
        self.assertEqual(self.db.data['payments']['pay-1']['resultCode'], 1037)

    def test_processing_code_4999_remains_pending(self):
        with patch.object(routes, 'query_stk_push_status', return_value={
            'status': 'FAILED', 'resultCode': 4999, 'description': 'Processing'
        }):
            result = routes.verify_and_finalize('pay-1')
        self.assertEqual(result['status'], 'PENDING')
        self.assertEqual(self.db.data['payments']['pay-1']['status'], 'PENDING')
        self.assertEqual(self.db.total_writes, 0)

    def test_duplicate_finalization_is_idempotent(self):
        result = {'status': 'SUCCESS', 'resultCode': 0, 'description': 'Completed'}
        self.assertEqual(routes.finalize_payment('pay-1', result), 'SUCCESS')
        first_writes = self.db.total_writes
        self.assertEqual(routes.finalize_payment('pay-1', result), 'SUCCESS')
        self.assertEqual(self.db.total_writes, first_writes)
        self.assertEqual(self.db.data['invoices']['inv-1']['status'], 'PAID')

    def test_unapproved_invoice_rejected_before_stk(self):
        self.db.data['invoices']['inv-1'].update({
            'status': 'PENDING_APPROVAL', 'paymentState': None,
            'currentPaymentId': None
        })
        with patch.object(routes, 'verify_client', return_value='client-1'), \
             patch.object(routes, 'initiate_stk_push') as stk:
            with self.assertRaises(routes.HTTPException) as caught:
                routes.initiate_payment(routes.InitiatePaymentInput(
                    invoiceId='inv-1', phoneNumber='0712345678'
                ))
        self.assertEqual(caught.exception.status_code, 409)
        stk.assert_not_called()
        self.assertEqual(self.db.total_writes, 0)


class DarajaResponseMappingTests(unittest.TestCase):
    def test_query_maps_success_cancellation_timeout_and_processing(self):
        cases = [(0, 'SUCCESS'), (1032, 'FAILED'), (1037, 'FAILED'), (4999, 'PENDING')]
        with patch.object(mpesa_service, 'get_access_token', return_value='fake-token'), \
             patch.dict(mpesa_service.os.environ, {
                 'DARAJA_SHORTCODE': '174379', 'DARAJA_PASSKEY': 'test-passkey'
             }):
            for code, expected in cases:
                with self.subTest(result_code=code):
                    response = unittest.mock.Mock()
                    response.json.return_value = {
                        'ResponseCode': '0', 'ResultCode': code, 'ResultDesc': 'Test'
                    }
                    response.raise_for_status.return_value = None
                    with patch.object(mpesa_service.requests, 'post', return_value=response):
                        result = mpesa_service.query_stk_push_status('checkout-1')
                    self.assertEqual(result['status'], expected)
                    self.assertEqual(result['resultCode'], code)


if __name__ == '__main__':
    unittest.main(verbosity=2)
